package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
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

            captureScreenshot(outputPath, captureWindow, request.timeoutMs());
            if (!Files.isRegularFile(outputPath)) {
                return ToolResult.failure("SCREENSHOT_ERROR", "screenshot file was not created", metrics(start, false));
            }
            if (Files.size(outputPath) <= 0L) {
                Files.deleteIfExists(outputPath);
                return ToolResult.failure("SCREENSHOT_ERROR", "screenshot file is empty", metrics(start, false));
            }
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("path", outputPath.toString());
            artifacts.put("captureWindow", captureWindow);
            artifacts.put("bytes", Files.size(outputPath));
            return ToolResult.success("desktop screenshot saved", artifacts, metrics(start, true));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failure("SCREENSHOT_ERROR", ex.getMessage(), metrics(start, false));
        } catch (Exception ex) {
            return ToolResult.failure("SCREENSHOT_ERROR", ex.getMessage(), metrics(start, false));
        }
    }

    private void captureScreenshot(Path outputPath, boolean captureWindow, long timeoutMs) throws Exception {
        if (captureWindow) {
            if (!PlatformSupport.isMac()) {
                throw new IllegalArgumentException("captureWindow is only supported on macOS");
            }
            runMacScreenCapture(outputPath, true, timeoutMs);
            return;
        }
        if (PlatformSupport.isMac()) {
            runMacScreenCapture(outputPath, false, timeoutMs);
            return;
        }
        captureWithRobot(outputPath);
    }

    private void runMacScreenCapture(Path outputPath, boolean captureWindow, long timeoutMs) throws Exception {
        ProcessBuilder processBuilder = captureWindow
                ? new ProcessBuilder("screencapture", "-x", "-w", outputPath.toString())
                : new ProcessBuilder("screencapture", "-x", outputPath.toString());
        Process process = processBuilder.start();
        if (!process.waitFor(Math.max(1, timeoutMs / 1000), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("desktop screenshot timed out");
        }
        if (process.exitValue() != 0) {
            String error = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException(error.isBlank() ? "failed to capture screenshot" : error);
        }
    }

    private void captureWithRobot(Path outputPath) throws Exception {
        Rectangle bounds = allScreenBounds();
        if (bounds.isEmpty()) {
            throw new IllegalStateException("no active screen detected");
        }
        BufferedImage image;
        try {
            image = new Robot().createScreenCapture(bounds);
        } catch (AWTException | HeadlessException ex) {
            throw new IllegalStateException("failed to capture screenshot: " + ex.getMessage(), ex);
        }
        if (!ImageIO.write(image, "png", outputPath.toFile())) {
            throw new IllegalStateException("failed to encode screenshot as png");
        }
    }

    private Rectangle allScreenBounds() {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle union = new Rectangle();
        for (GraphicsDevice device : environment.getScreenDevices()) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            union = union.isEmpty() ? new Rectangle(bounds) : union.union(bounds);
        }
        return union;
    }

    private ObjectNode metrics(long start, boolean success) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("success", success);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
