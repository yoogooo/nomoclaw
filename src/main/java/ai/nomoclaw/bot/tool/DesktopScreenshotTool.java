package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class DesktopScreenshotTool implements Tool {

    @Override
    public String name() {
        return "desktop_screenshot_tool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String output = request.args().path("path").asText("");
            boolean captureWindow = request.args().path("captureWindow").asBoolean(false);
            Path outputPath = output == null || output.isBlank()
                    ? request.tmpDirectory().resolve(UUID.randomUUID() + ".png").toAbsolutePath().normalize()
                    : PathResolver.resolveInAgentWorkspace(output, request);
            Files.createDirectories(outputPath.getParent());

            ProcessBuilder processBuilder = captureWindow
                    ? new ProcessBuilder("screencapture", "-x", "-w", outputPath.toString())
                    : new ProcessBuilder("screencapture", "-x", outputPath.toString());
            Process process = processBuilder.start();
            if (!process.waitFor(Math.max(1, request.timeoutMs() / 1000), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return ToolResult.failure("TIMEOUT", "desktop screenshot timed out", metrics(start, false));
            }
            if (process.exitValue() != 0 || !Files.exists(outputPath)) {
                String error = new String(process.getErrorStream().readAllBytes());
                return ToolResult.failure("SCREENSHOT_ERROR", error.isBlank() ? "failed to capture screenshot" : error, metrics(start, false));
            }
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("path", outputPath.toString());
            artifacts.put("captureWindow", captureWindow);
            return ToolResult.success("desktop screenshot saved", artifacts, metrics(start, true));
        } catch (Exception ex) {
            return ToolResult.failure("SCREENSHOT_ERROR", ex.getMessage(), metrics(start, false));
        }
    }

    private ObjectNode metrics(long start, boolean success) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("success", success);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
