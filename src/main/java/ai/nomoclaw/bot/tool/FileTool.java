package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.policy.tool.ToolPolicyContext;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecisionResult;
import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class FileTool implements Tool {

    private final ToolPermissionPolicyService toolPermissionPolicyService;

    public FileTool(ToolPermissionPolicyService toolPermissionPolicyService) {
        this.toolPermissionPolicyService = toolPermissionPolicyService;
    }

    @Override
    public String name() {
        return "file_tool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String action = request.args().path("action").asText("");
            String pathRaw = request.args().path("path").asText("");
            log.info("[Tool][file] execute conversationUid={} messageUid={} stepUid={} action={} path={}",
                    request.conversationUid(), request.messageUid(), request.stepUid(), action, pathRaw);
            if (pathRaw.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "path is required", metric(start, 0));
            }

            ToolPolicyDecisionResult policyDecision = toolPermissionPolicyService.evaluate(new ToolPolicyContext(
                    name(),
                    request.args(),
                    request.agentWorkspacePath(),
                    "",
                    request.conversationUid(),
                    request.messageUid(),
                    request.stepUid()
            ));
            if (policyDecision.denied() || policyDecision.asks()) {
                return ToolResult.failure(
                        policyDecision.reasonCode().name(),
                        policyDecision.message().isBlank() ? "当前文件操作被安全策略阻止。" : policyDecision.message(),
                        metric(start, 0)
                );
            }

            Path path = PathResolver.resolveInAgentWorkspace(pathRaw, request);
            return switch (action) {
                case "read" -> {
                    String content = Files.readString(path);
                    int startLine = Math.max(1, request.args().path("startLine").asInt(1));
                    int endLine = request.args().has("endLine")
                            ? Math.max(startLine, request.args().path("endLine").asInt(startLine))
                            : Integer.MAX_VALUE;
                    List<String> lines = List.of(content.split("\\R", -1));
                    int actualEnd = Math.min(lines.size(), endLine);
                    StringBuilder builder = new StringBuilder();
                    for (int i = startLine - 1; i < actualEnd; i++) {
                        builder.append(i + 1).append(": ").append(lines.get(i)).append('\n');
                    }
                    ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
                    artifacts.put("path", path.toAbsolutePath().toString());
                    artifacts.put("length", content.length());
                    artifacts.put("startLine", startLine);
                    artifacts.put("endLine", actualEnd);
                    yield ToolResult.success(ToolTextUtils.truncateHead(builder.toString().trim()), artifacts, metric(start, content.length()));
                }
                case "list" -> {
                    ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
                    ArrayNode items = JsonNodeFactory.instance.arrayNode();
                    try (var stream = Files.list(path)) {
                        stream.map(p -> p.toAbsolutePath().toString()).sorted().forEach(items::add);
                    }
                    artifacts.set("items", items);
                    yield ToolResult.success(ToolTextUtils.truncateHead(JsonUtil.toJson(items)), artifacts, metric(start, items.size()));
                }
                case "write" -> {
                    String content = request.args().path("content").asText("");
                    Files.createDirectories(path.getParent() == null ? Paths.get(".") : path.getParent());
                    Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
                    artifacts.put("path", path.toAbsolutePath().toString());
                    artifacts.put("bytes", content.getBytes(StandardCharsets.UTF_8).length);
                    yield ToolResult.success("file written", artifacts, metric(start, content.length()));
                }
                case "append" -> {
                    String content = request.args().path("content").asText("");
                    Files.createDirectories(path.getParent() == null ? Paths.get(".") : path.getParent());
                    Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
                    artifacts.put("path", path.toAbsolutePath().toString());
                    artifacts.put("bytes", content.getBytes(StandardCharsets.UTF_8).length);
                    yield ToolResult.success("file appended", artifacts, metric(start, content.length()));
                }
                case "edit" -> {
                    String oldText = request.args().path("oldText").asText("");
                    String newText = request.args().path("newText").asText("");
                    String content = Files.readString(path);
                    if (oldText.isBlank()) {
                        yield ToolResult.failure("INVALID_ARGS", "oldText is required for edit", metric(start, 0));
                    }
                    if (!content.contains(oldText)) {
                        yield ToolResult.failure("INVALID_ARGS", "oldText not found in file", metric(start, 0));
                    }
                    String updated = content.replace(oldText, newText);
                    Files.writeString(path, updated, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                    ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
                    artifacts.put("path", path.toAbsolutePath().toString());
                    artifacts.put("replaced", oldText);
                    yield ToolResult.success("file edited", artifacts, metric(start, updated.length()));
                }
                default -> ToolResult.failure("INVALID_ACTION", "unsupported file action: " + action, metric(start, 0));
            };
        } catch (Exception ex) {
            log.warn("[Tool][file] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
            return ToolResult.failure("FILE_ERROR", ex.getMessage(), metric(start, 0));
        }
    }

    private ObjectNode metric(long start, int size) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("size", size);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
