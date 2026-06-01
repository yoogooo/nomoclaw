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
public class EditFileTool implements Tool {

    @Override
    public String name() {
        return "EditFileTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String pathRaw = request.args().path("path").asString("");
            String oldText = request.args().path("oldText").asString("");
            String newText = request.args().path("newText").asString("");
            log.info("[Tool][edit-file] execute conversationUid={} messageUid={} stepUid={} path={}",
                    request.conversationUid(), request.messageUid(), request.stepUid(), pathRaw);
            if (pathRaw.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "path is required", metric(start, 0));
            }
            if (oldText.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "oldText is required for edit", metric(start, 0));
            }

            Path path = PathResolver.resolveInAgentWorkspace(pathRaw, request);
            String content = Files.readString(path);
            if (!content.contains(oldText)) {
                return ToolResult.failure("INVALID_ARGS", "oldText not found in file", metric(start, 0));
            }
            String updated = content.replace(oldText, newText);
            Files.writeString(path, updated, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("path", path.toAbsolutePath().toString());
            artifacts.put("replaced", oldText);
            return ToolResult.success("file edited", artifacts, metric(start, updated.length()));
        } catch (Exception ex) {
            log.warn("[Tool][edit-file] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
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
