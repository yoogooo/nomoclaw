package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NotDirectoryException;
import java.time.Instant;

@Component
@Slf4j
public class ListFileTool implements Tool {

    @Override
    public String name() {
        return "ListFileTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String pathRaw = request.args().path("path").asString("");
            log.info("[Tool][list-file] execute conversationUid={} messageUid={} stepUid={} path={}",
                    request.conversationUid(), request.messageUid(), request.stepUid(), pathRaw);
            if (pathRaw.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "path is required", metric(start, 0));
            }

            Path path = PathResolver.resolveInAgentWorkspace(pathRaw, request);
            if (!Files.exists(path)) {
                return ToolResult.failure("INVALID_ARGS", "directory does not exist: " + path, metric(start, 0));
            }
            if (!Files.isDirectory(path)) {
                return ToolResult.failure("INVALID_ARGS", "path is not a directory: " + path, metric(start, 0));
            }
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            ArrayNode items = JsonNodeFactory.instance.arrayNode();
            try (var stream = Files.list(path)) {
                stream.map(p -> p.toAbsolutePath().toString()).sorted().forEach(items::add);
            }
            artifacts.set("items", items);
            artifacts.put("path", path.toAbsolutePath().toString());
            return ToolResult.success(ToolTextUtils.truncateHead(JsonUtil.toJson(items)), artifacts, metric(start, items.size()));
        } catch (NoSuchFileException ex) {
            log.warn("[Tool][list-file] missing directory stepUid={} err={}", request.stepUid(), ex.getMessage());
            return ToolResult.failure("INVALID_ARGS", "directory does not exist: " + ex.getFile(), metric(start, 0));
        } catch (NotDirectoryException ex) {
            log.warn("[Tool][list-file] not directory stepUid={} err={}", request.stepUid(), ex.getMessage());
            return ToolResult.failure("INVALID_ARGS", "path is not a directory: " + ex.getFile(), metric(start, 0));
        } catch (Exception ex) {
            log.warn("[Tool][list-file] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
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
