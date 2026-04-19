package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.util.CommandShellResolver;
import ai.nomoclaw.bot.util.CommandShellResolver.CommandShell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CommandTool implements Tool {
    private static final Set<String> READ_ONLY_COMMAND_HEADS = Set.of(
            "du", "df", "ls", "find", "stat", "wc", "grep", "awk", "sed", "sort", "head", "tail", "cut", "uniq",
            "ps", "top", "vm_stat", "iostat"
    );

    private final MessageCancellationRegistry cancellationRegistry;
    private final CommandShell commandShell = CommandShellResolver.resolve();

    public CommandTool(MessageCancellationRegistry cancellationRegistry) {
        this.cancellationRegistry = cancellationRegistry;
    }

    @Override
    public String name() {
        return "command_tool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        String command = request.args().path("command").asText("");
        String cwd = request.args().path("cwd").asText("");
        log.info("[Tool][command] execute conversationUid={} messageUid={} stepUid={} cwd={} cmd={}",
                request.conversationUid(), request.messageUid(), request.stepUid(), cwd, command);
        if (command.isBlank()) {
            return ToolResult.failure("INVALID_ARGS", "command is required", metric(start, -1, false));
        }

        Path workingDir = cwd == null || cwd.isBlank()
                ? request.agentWorkspacePath()
                : PathResolver.resolveInAgentWorkspace(cwd, request);
        if (!workingDir.toFile().exists() || !workingDir.toFile().isDirectory()) {
            return ToolResult.failure("INVALID_ARGS", "cwd is not a directory: " + workingDir, metric(start, -1, false));
        }

        ProcessBuilder processBuilder = new ProcessBuilder(commandShell.command(command));
        processBuilder.directory(workingDir.toFile());

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readText(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readText(process.getErrorStream()));
            long deadline = System.currentTimeMillis() + request.timeoutMs();
            while (System.currentTimeMillis() < deadline) {
                if (cancellationRegistry.isCanceled(request.messageUid())) {
                    process.destroyForcibly();
                    return ToolResult.failure("CANCELLED", "message canceled", metric(start, -1, false));
                }
                if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    int exitCode = process.exitValue();
                    String stdout = stdoutFuture.join();
                    String stderr = stderrFuture.join();
                    log.info("[Tool][command] finished stepUid={} exitCode={} stdoutLen={} stderrLen={}",
                            request.stepUid(), exitCode, stdout.length(), stderr.length());
                    ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
                    artifacts.put("cwd", workingDir.toString());
                    artifacts.put("command", command);
                    artifacts.put("shell", commandShell.displayName());
                    artifacts.put("stdout", ToolTextUtils.truncateTail(stdout));
                    artifacts.put("stderr", ToolTextUtils.truncateTail(stderr));
                    artifacts.put("exitCode", exitCode);
                    String output = """
                            <returncode>%d</returncode>
                            <stdout>
                            %s
                            </stdout>
                            <stderr>
                            %s
                            </stderr>
                            """.formatted(exitCode, ToolTextUtils.truncateTail(stdout), ToolTextUtils.truncateTail(stderr)).trim();
                    if (exitCode == 0) {
                        return ToolResult.success(output, artifacts, metric(start, exitCode, false));
                    }
                    if (shouldAcceptNonZeroExit(command, exitCode, stdout, stderr)) {
                        artifacts.put("nonZeroExitAccepted", true);
                        return ToolResult.success(output, artifacts, metric(start, exitCode, false));
                    }
                    return ToolResult.failure("NON_ZERO_EXIT", output, metric(start, exitCode, false));
                }
            }
            process.destroyForcibly();
            return ToolResult.failure("TIMEOUT", "command timed out", metric(start, -1, true));
        } catch (Exception ex) {
            log.warn("[Tool][command] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
            return ToolResult.failure("COMMAND_ERROR", ex.getMessage(), metric(start, -1, false));
        }
    }

    private String readText(java.io.InputStream inputStream) {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private boolean shouldAcceptNonZeroExit(String command, int exitCode, String stdout, String stderr) {
        if (exitCode == 0) {
            return false;
        }
        if (stdout == null || stdout.isBlank()) {
            return false;
        }
        if (stderr != null && !stderr.isBlank()) {
            return false;
        }
        String normalized = command == null ? "" : command.trim();
        if (normalized.isBlank()) {
            return false;
        }
        String[] pipelineSplit = normalized.split("[|;&]{1,2}", 2);
        String firstSegment = pipelineSplit.length == 0 ? normalized : pipelineSplit[0].trim();
        if (firstSegment.isBlank()) {
            return false;
        }
        String[] tokens = firstSegment.split("\\s+");
        if (tokens.length == 0) {
            return false;
        }
        String head = tokens[0].toLowerCase(Locale.ROOT);
        if ("sudo".equals(head) && tokens.length > 1) {
            head = tokens[1].toLowerCase(Locale.ROOT);
        }
        return READ_ONLY_COMMAND_HEADS.contains(head);
    }

    private ObjectNode metric(long start, int exitCode, boolean timeout) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("exitCode", exitCode);
        metrics.put("timeout", timeout);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
