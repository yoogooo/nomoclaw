package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Component
public class SendFileTool implements Tool {

    @Override
    public String name() {
        return "send_file_tool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String filePath = request.args().path("filePath").asText("");
            if (filePath.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "filePath is required", metrics(start));
            }
            Path path = PathResolver.resolveInAgentWorkspace(filePath, request);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return ToolResult.failure("INVALID_ARGS", "file does not exist: " + path, metrics(start));
            }
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("path", path.toString());
            artifacts.put("filename", path.getFileName().toString());
            artifacts.put("mimeType", Files.probeContentType(path) == null ? "application/octet-stream" : Files.probeContentType(path));
            artifacts.put("size", Files.size(path));
            return ToolResult.success("已成功发送文件", artifacts, metrics(start));
        } catch (Exception ex) {
            return ToolResult.failure("SEND_FILE_ERROR", ex.getMessage(), metrics(start));
        }
    }

    private ObjectNode metrics(long start) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
