package ai.nomoclaw.bot.mcp;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.store.entity.McpServerDefinitionEntity;
import ai.nomoclaw.bot.store.entity.McpToolSnapshotEntity;
import ai.nomoclaw.bot.util.JsonUtil;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Bridges Codex MCP tool invocations to `codex app-server` so streamed agent events can be surfaced
 * as step progress on the chat page.
 */
@Service
public class CodexAppServerBridge {

    private static final String TOOL_CODEX = "codex";
    private static final String TOOL_CODEX_REPLY = "codex-reply";
    private static final String EOF_MARKER = "__CODEX_APP_SERVER_STDOUT_EOF__";
    private static final int INIT_REQUEST_ID = 1;
    private static final int THREAD_REQUEST_ID = 2;
    private static final int TURN_REQUEST_ID = 3;
    private static final long PROGRESS_REPORT_INTERVAL_MS = 700L;
    private static final int MAX_PROGRESS_DETAILS_CHARS = 12_000;
    private static final int MAX_FINAL_OUTPUT_CHARS = 64_000;

    public boolean supports(McpToolSnapshotEntity snapshot, McpServerDefinitionEntity server, McpServerConfig config) {
        if (snapshot == null || server == null || config == null) {
            return false;
        }
        String originalToolName = normalize(snapshot.getOriginalToolName());
        if (!TOOL_CODEX.equals(originalToolName) && !TOOL_CODEX_REPLY.equals(originalToolName)) {
            return false;
        }
        String command = normalize(config.command());
        if (command.isBlank()) {
            return false;
        }
        Path commandPath = Path.of(command);
        String executableName = normalize(commandPath.getFileName() == null ? command : commandPath.getFileName().toString());
        return "codex".equals(executableName);
    }

    public ToolResult execute(McpToolSnapshotEntity snapshot,
                              McpServerDefinitionEntity server,
                              McpServerConfig config,
                              ToolRequest request) {
        String originalToolName = normalize(snapshot.getOriginalToolName());
        String prompt = normalize(request.args().path("prompt").asText(""));
        if (prompt.isBlank()) {
            return ToolResult.failure("INVALID_ARGS", "prompt is required", JsonNodeFactory.instance.objectNode());
        }
        if (TOOL_CODEX_REPLY.equals(originalToolName) && resolveThreadId(request.args()).isBlank()) {
            return ToolResult.failure("INVALID_ARGS", "threadId is required for codex-reply", JsonNodeFactory.instance.objectNode());
        }

        AppServerSession session = new AppServerSession(snapshot, server, config, request);
        try {
            return session.run();
        } catch (Exception ex) {
            ObjectNode metrics = JsonNodeFactory.instance.objectNode();
            metrics.put("serverUid", server.getServerUid());
            metrics.put("toolName", snapshot.getOriginalToolName());
            return ToolResult.failure("CODEX_APP_SERVER_ERROR", ex.getMessage(), metrics);
        }
    }

    private String resolveThreadId(JsonNode args) {
        String threadId = normalize(args.path("threadId").asText(""));
        if (!threadId.isBlank()) {
            return threadId;
        }
        return normalize(args.path("conversationId").asText(""));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private final class AppServerSession {

        private final McpToolSnapshotEntity snapshot;
        private final McpServerDefinitionEntity server;
        private final McpServerConfig config;
        private final ToolRequest request;
        private final String toolName;
        private final String prompt;
        private final String requestedThreadId;
        private final BlockingQueue<String> stdoutQueue = new LinkedBlockingQueue<>();
        private final StringBuilder stderrBuffer = new StringBuilder();
        private final StringBuilder agentMessageBuffer = new StringBuilder();
        private final StringBuilder reasoningBuffer = new StringBuilder();
        private final StringBuilder commandOutputBuffer = new StringBuilder();
        private final StringBuilder fileChangeBuffer = new StringBuilder();

        private Process process;
        private BufferedWriter writer;
        private String threadId = "";
        private String turnId = "";
        private String phase = "starting";
        private String turnStatus = "";
        private String turnErrorMessage = "";
        private long startedAtMillis;
        private long lastProgressReportAt;

        private AppServerSession(McpToolSnapshotEntity snapshot,
                                 McpServerDefinitionEntity server,
                                 McpServerConfig config,
                                 ToolRequest request) {
            this.snapshot = snapshot;
            this.server = server;
            this.config = config;
            this.request = request;
            this.toolName = normalize(snapshot.getOriginalToolName());
            this.prompt = normalize(request.args().path("prompt").asText(""));
            this.requestedThreadId = resolveThreadId(request.args());
        }

        private ToolResult run() throws Exception {
            startedAtMillis = System.currentTimeMillis();
            startProcess();
            sendInitialize();
            sendInitialized();
            startOrResumeThread();
            reportProgress(true);
            startTurn();
            waitForTurnCompletion();
            return toResult();
        }

        private void startProcess() throws IOException {
            List<String> command = new ArrayList<>();
            command.add(config.command());
            command.add("app-server");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Path processWorkingDirectory = resolveProcessWorkingDirectory();
            processBuilder.directory(processWorkingDirectory.toFile());
            if (config.env() != null && !config.env().isEmpty()) {
                processBuilder.environment().putAll(config.env());
            }
            process = processBuilder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            startStdoutPump(process);
            startStderrPump(process);
        }

        private Path resolveProcessWorkingDirectory() {
            String configCwd = normalize(config.cwd());
            if (!configCwd.isBlank()) {
                return Path.of(configCwd).toAbsolutePath().normalize();
            }
            return request.agentWorkspacePath().toAbsolutePath().normalize();
        }

        private void startStdoutPump(Process runningProcess) {
            Thread.startVirtualThread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutQueue.offer(line);
                    }
                } catch (IOException ignored) {
                    // Ignore and let EOF handling surface transport exit in the main loop.
                } finally {
                    stdoutQueue.offer(EOF_MARKER);
                }
            });
        }

        private void startStderrPump(Process runningProcess) {
            Thread.startVirtualThread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stderrBuffer) {
                            if (!stderrBuffer.isEmpty()) {
                                stderrBuffer.append('\n');
                            }
                            stderrBuffer.append(line);
                            if (stderrBuffer.length() > MAX_PROGRESS_DETAILS_CHARS) {
                                stderrBuffer.delete(0, stderrBuffer.length() - MAX_PROGRESS_DETAILS_CHARS);
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // Ignore stderr read errors; main execution path will still capture process exit.
                }
            });
        }

        private void sendInitialize() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            ObjectNode clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "nomoclaw");
            clientInfo.put("title", "NomoClaw");
            clientInfo.put("version", "0.0.1");
            sendRequest("initialize", INIT_REQUEST_ID, params);
            JsonNode response = waitForResponse(INIT_REQUEST_ID);
            ensureSuccessResponse(response, "initialize");
        }

        private void sendInitialized() throws IOException {
            sendNotification("initialized", JsonNodeFactory.instance.objectNode());
        }

        private void startOrResumeThread() throws Exception {
            if (TOOL_CODEX_REPLY.equals(toolName)) {
                ObjectNode params = JsonNodeFactory.instance.objectNode();
                params.put("threadId", requestedThreadId);
                applyThreadOverrides(params);
                sendRequest("thread/resume", THREAD_REQUEST_ID, params);
                JsonNode response = waitForResponse(THREAD_REQUEST_ID);
                ensureSuccessResponse(response, "thread/resume");
                threadId = normalize(response.path("result").path("thread").path("id").asText(""));
                if (threadId.isBlank()) {
                    threadId = requestedThreadId;
                }
                return;
            }

            ObjectNode params = JsonNodeFactory.instance.objectNode();
            applyThreadOverrides(params);
            params.put("serviceName", server.getServerName());
            sendRequest("thread/start", THREAD_REQUEST_ID, params);
            JsonNode response = waitForResponse(THREAD_REQUEST_ID);
            ensureSuccessResponse(response, "thread/start");
            threadId = normalize(response.path("result").path("thread").path("id").asText(""));
            if (threadId.isBlank()) {
                throw new IllegalStateException("thread/start returned empty thread id");
            }
        }

        private void applyThreadOverrides(ObjectNode params) {
            putIfPresent(params, "cwd", resolveTurnCwd());
            putIfPresent(params, "model", normalize(request.args().path("model").asText("")));
            putIfPresent(params, "approvalPolicy", normalize(request.args().path("approval-policy").asText("")));
            putIfPresent(params, "sandbox", normalize(request.args().path("sandbox").asText("")));
            putIfPresent(params, "baseInstructions", normalize(request.args().path("base-instructions").asText("")));
            putIfPresent(params, "developerInstructions", normalize(request.args().path("developer-instructions").asText("")));
            JsonNode configNode = request.args().path("config");
            if (configNode != null && configNode.isObject()) {
                params.set("config", configNode.deepCopy());
            }
        }

        private String resolveTurnCwd() {
            String requestedCwd = normalize(request.args().path("cwd").asText(""));
            if (requestedCwd.isBlank()) {
                return request.agentWorkspacePath().toAbsolutePath().normalize().toString();
            }
            Path path = Path.of(requestedCwd);
            if (path.isAbsolute()) {
                return path.normalize().toString();
            }
            String base = normalize(config.cwd());
            Path basePath = base.isBlank()
                    ? request.agentWorkspacePath().toAbsolutePath().normalize()
                    : Path.of(base).toAbsolutePath().normalize();
            return basePath.resolve(path).normalize().toString();
        }

        private void startTurn() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("threadId", threadId);
            params.putArray("input")
                    .addObject()
                    .put("type", "text")
                    .put("text", prompt);
            sendRequest("turn/start", TURN_REQUEST_ID, params);
            JsonNode response = waitForResponse(TURN_REQUEST_ID);
            ensureSuccessResponse(response, "turn/start");
            turnId = normalize(response.path("result").path("turn").path("id").asText(""));
            phase = "running";
        }

        private void waitForTurnCompletion() throws Exception {
            while (true) {
                ensureNotTimedOut();
                String line = pollStdoutLine();
                if (line == null) {
                    reportProgress(false);
                    continue;
                }
                if (EOF_MARKER.equals(line)) {
                    int exitCode = process.waitFor();
                    throw new IllegalStateException("codex app-server exited unexpectedly: exitCode=" + exitCode + stderrSuffix());
                }
                JsonNode message = JsonUtil.mapper().readTree(line);
                if (isResponse(message)) {
                    continue;
                }
                if (isServerRequest(message)) {
                    respondUnsupportedServerRequest(message);
                    continue;
                }
                handleNotification(message);
                if (!turnStatus.isBlank()) {
                    return;
                }
            }
        }

        private String pollStdoutLine() throws InterruptedException {
            return stdoutQueue.poll(250, TimeUnit.MILLISECONDS);
        }

        private void ensureNotTimedOut() {
            if (request.timeoutMs() <= 0) {
                return;
            }
            if (System.currentTimeMillis() - startedAtMillis > request.timeoutMs()) {
                if (process != null) {
                    process.destroyForcibly();
                }
                throw new IllegalStateException("codex app-server timed out after " + request.timeoutMs() + " ms" + stderrSuffix());
            }
        }

        private boolean isResponse(JsonNode message) {
            return message.has("id") && (message.has("result") || message.has("error"));
        }

        private boolean isServerRequest(JsonNode message) {
            return message.has("id") && message.has("method") && !message.has("result") && !message.has("error");
        }

        private void respondUnsupportedServerRequest(JsonNode message) throws IOException {
            ObjectNode response = JsonNodeFactory.instance.objectNode();
            response.put("id", message.path("id").asInt());
            ObjectNode error = response.putObject("error");
            error.put("code", -32000);
            error.put("message", "nomoclaw codex app-server bridge does not support interactive server requests yet");
            writeMessage(response);
        }

        private void handleNotification(JsonNode message) {
            String method = normalize(message.path("method").asText(""));
            JsonNode params = message.path("params");
            switch (method) {
                case "item/agentMessage/delta" -> {
                    phase = "agentMessage";
                    append(agentMessageBuffer, params.path("delta").asText(""));
                    reportProgress(false);
                }
                case "item/reasoning/textDelta", "item/reasoningSummary/textDelta" -> {
                    phase = "reasoning";
                    append(reasoningBuffer, params.path("delta").asText(""));
                    reportProgress(false);
                }
                case "item/commandExecution/outputDelta", "command/exec/outputDelta" -> {
                    phase = "commandExecution";
                    append(commandOutputBuffer, params.path("delta").asText(""));
                    reportProgress(false);
                }
                case "item/fileChange/outputDelta" -> {
                    phase = "fileChange";
                    append(fileChangeBuffer, params.path("delta").asText(""));
                    reportProgress(false);
                }
                case "item/started" -> {
                    JsonNode item = params.path("item");
                    String itemType = normalize(item.path("type").asText(""));
                    if (!itemType.isBlank()) {
                        phase = itemType;
                    }
                    reportProgress(true);
                }
                case "item/completed" -> reportProgress(true);
                case "turn/completed" -> {
                    JsonNode turn = params.path("turn");
                    turnStatus = normalize(turn.path("status").asText(""));
                    if (turnId.isBlank()) {
                        turnId = normalize(turn.path("id").asText(""));
                    }
                    JsonNode errorNode = turn.path("error");
                    if (errorNode != null && !errorNode.isMissingNode() && !errorNode.isNull()) {
                        turnErrorMessage = firstNonBlank(
                                normalize(errorNode.path("message").asText("")),
                                normalize(errorNode.toString())
                        );
                    }
                    reportProgress(true);
                }
                case "error" -> {
                    turnErrorMessage = firstNonBlank(
                            normalize(params.path("error").path("message").asText("")),
                            normalize(params.path("message").asText("")),
                            turnErrorMessage
                    );
                    reportProgress(true);
                }
                default -> {
                    // Ignore unsupported notifications; only a subset is needed for chat streaming.
                }
            }
        }

        private void append(StringBuilder buffer, String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            buffer.append(delta);
            if (buffer.length() > MAX_FINAL_OUTPUT_CHARS) {
                buffer.delete(0, buffer.length() - MAX_FINAL_OUTPUT_CHARS);
            }
        }

        private void reportProgress(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && now - lastProgressReportAt < PROGRESS_REPORT_INTERVAL_MS) {
                return;
            }
            lastProgressReportAt = now;
            String summary = switch (phase) {
                case "reasoning" -> "Codex 正在推理";
                case "commandExecution" -> "Codex 正在执行命令";
                case "fileChange" -> "Codex 正在修改文件";
                case "agentMessage" -> "Codex 正在生成回复";
                case "running" -> "Codex 正在执行";
                default -> "Codex 正在启动";
            };
            ObjectNode metrics = JsonNodeFactory.instance.objectNode();
            putIfPresent(metrics, "threadId", threadId);
            putIfPresent(metrics, "turnId", turnId);
            putIfPresent(metrics, "phase", phase);
            metrics.put("bridge", "codex-app-server");
            request.reportProgress(summary, buildProgressDetails(), metrics);
        }

        private String buildProgressDetails() {
            StringBuilder builder = new StringBuilder();
            if (!threadId.isBlank()) {
                builder.append("Thread: ").append(threadId).append('\n');
            }
            if (!turnId.isBlank()) {
                builder.append("Turn: ").append(turnId).append('\n');
            }
            builder.append("Phase: ").append(phase).append('\n');
            appendSection(builder, "Agent", agentMessageBuffer);
            appendSection(builder, "Reasoning", reasoningBuffer);
            appendSection(builder, "Command Output", commandOutputBuffer);
            appendSection(builder, "File Changes", fileChangeBuffer);
            synchronized (stderrBuffer) {
                if (!stderrBuffer.isEmpty()) {
                    appendSection(builder, "Stderr", stderrBuffer);
                }
            }
            String result = builder.toString().trim();
            if (result.length() <= MAX_PROGRESS_DETAILS_CHARS) {
                return result;
            }
            return result.substring(result.length() - MAX_PROGRESS_DETAILS_CHARS);
        }

        private void appendSection(StringBuilder target, String label, CharSequence content) {
            if (content == null || content.isEmpty()) {
                return;
            }
            target.append('\n').append(label).append(":\n");
            String text = content.toString();
            if (text.length() > 4000) {
                text = text.substring(text.length() - 4000);
            }
            target.append(text).append('\n');
        }

        private ToolResult toResult() {
            String status = turnStatus.isBlank() ? "unknown" : turnStatus;
            String finalOutput = firstNonBlank(
                    normalize(agentMessageBuffer.toString()),
                    normalize(commandOutputBuffer.toString()),
                    buildProgressDetails()
            );
            if (finalOutput.length() > MAX_FINAL_OUTPUT_CHARS) {
                finalOutput = finalOutput.substring(finalOutput.length() - MAX_FINAL_OUTPUT_CHARS);
            }
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("serverUid", server.getServerUid());
            artifacts.put("serverName", server.getServerName());
            artifacts.put("originalToolName", snapshot.getOriginalToolName());
            putIfPresent(artifacts, "threadId", threadId);
            putIfPresent(artifacts, "turnId", turnId);
            artifacts.put("status", status);

            ObjectNode metrics = JsonNodeFactory.instance.objectNode();
            metrics.put("bridge", "codex-app-server");
            metrics.put("durationMs", System.currentTimeMillis() - startedAtMillis);
            putIfPresent(metrics, "phase", phase);

            closeProcess();
            if ("completed".equals(status)) {
                return ToolResult.success(finalOutput, artifacts, metrics);
            }
            String errorMessage = firstNonBlank(turnErrorMessage, "codex turn finished with status: " + status);
            return ToolResult.failure("CODEX_TURN_" + status.toUpperCase(Locale.ROOT), errorMessage, metrics);
        }

        private void closeProcess() {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ignored) {
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }

        private JsonNode waitForResponse(int requestId) throws Exception {
            while (true) {
                ensureNotTimedOut();
                String line = pollStdoutLine();
                if (line == null) {
                    continue;
                }
                if (EOF_MARKER.equals(line)) {
                    int exitCode = process.waitFor();
                    throw new IllegalStateException("codex app-server exited unexpectedly before response: exitCode=" + exitCode + stderrSuffix());
                }
                JsonNode message = JsonUtil.mapper().readTree(line);
                if (isResponse(message) && message.path("id").asInt() == requestId) {
                    return message;
                }
                if (isServerRequest(message)) {
                    respondUnsupportedServerRequest(message);
                    continue;
                }
                if (!isResponse(message)) {
                    handleNotification(message);
                }
            }
        }

        private void ensureSuccessResponse(JsonNode response, String method) {
            JsonNode errorNode = response.path("error");
            if (errorNode == null || errorNode.isMissingNode() || errorNode.isNull()) {
                return;
            }
            String message = firstNonBlank(
                    normalize(errorNode.path("message").asText("")),
                    normalize(errorNode.toString())
            );
            throw new IllegalStateException(method + " failed: " + message + stderrSuffix());
        }

        private void sendRequest(String method, int requestId, JsonNode params) throws IOException {
            ObjectNode requestNode = JsonNodeFactory.instance.objectNode();
            requestNode.put("jsonrpc", "2.0");
            requestNode.put("method", method);
            requestNode.put("id", requestId);
            requestNode.set("params", params == null ? JsonNodeFactory.instance.objectNode() : params);
            writeMessage(requestNode);
        }

        private void sendNotification(String method, JsonNode params) throws IOException {
            ObjectNode requestNode = JsonNodeFactory.instance.objectNode();
            requestNode.put("jsonrpc", "2.0");
            requestNode.put("method", method);
            requestNode.set("params", params == null ? JsonNodeFactory.instance.objectNode() : params);
            writeMessage(requestNode);
        }

        private void writeMessage(JsonNode node) throws IOException {
            writer.write(JsonUtil.toJson(node));
            writer.write('\n');
            writer.flush();
        }

        private void putIfPresent(ObjectNode objectNode, String field, String value) {
            if (objectNode == null) {
                return;
            }
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                objectNode.put(field, normalized);
            }
        }

        private String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "";
        }

        private String stderrSuffix() {
            synchronized (stderrBuffer) {
                if (stderrBuffer.isEmpty()) {
                    return "";
                }
                return " stderr=" + stderrBuffer;
            }
        }
    }
}
