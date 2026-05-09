package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@Component
@Slf4j
public class CreateFileTool implements Tool {

    @Override
    public String name() {
        return "CreateFileTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String pathRaw = request.args().path("path").asString("");
            String mode = request.args().path("mode").asString("create_or_truncate");
            String content = request.args().path("content").asString("");
            log.info("[Tool][create-file] execute conversationUid={} messageUid={} stepUid={} mode={} path={}",
                    request.conversationUid(), request.messageUid(), request.stepUid(), mode, pathRaw);
            if (pathRaw.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "path is required", metric(start, 0));
            }
            if (!"create_or_truncate".equals(mode) && !"append".equals(mode)) {
                return ToolResult.failure("INVALID_ARGS", "mode must be create_or_truncate or append", metric(start, 0));
            }
            if (!request.args().has("content")) {
                return ToolResult.failure("INVALID_ARGS", "content is required", metric(start, 0));
            }

            Path path = PathResolver.resolveInAgentWorkspace(pathRaw, request);
            Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
            if ("append".equals(mode)) {
                Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("path", path.toAbsolutePath().toString());
            artifacts.put("mode", mode);
            artifacts.put("bytes", content.getBytes(StandardCharsets.UTF_8).length);
            return ToolResult.success("file written", artifacts, metric(start, content.length()));
        } catch (Exception ex) {
            log.warn("[Tool][create-file] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
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
