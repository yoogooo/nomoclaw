package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class ReadFileTool implements Tool {

    @Override
    public String name() {
        return "ReadFileTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String pathRaw = request.args().path("path").asString("");
            log.info("[Tool][read-file] execute conversationUid={} messageUid={} stepUid={} path={}",
                    request.conversationUid(), request.messageUid(), request.stepUid(), pathRaw);
            if (pathRaw.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "path is required", metric(start, 0));
            }

            Path path = PathResolver.resolveInAgentWorkspace(pathRaw, request);
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
            return ToolResult.success(ToolTextUtils.truncateHead(builder.toString().trim()), artifacts, metric(start, content.length()));
        } catch (Exception ex) {
            log.warn("[Tool][read-file] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
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
