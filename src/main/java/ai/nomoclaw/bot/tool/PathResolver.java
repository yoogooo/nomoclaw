package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.workspace.NomoClawPaths;
import ai.nomoclaw.bot.model.ToolRequest;

import java.nio.file.Path;

public final class PathResolver {

    private PathResolver() {
    }

    public static Path resolve(String rawPath) {
        String normalized = expandHome(rawPath == null || rawPath.isBlank() ? "." : rawPath.trim());
        return Path.of(normalized).toAbsolutePath().normalize();
    }

    public static Path resolve(String rawPath, Path baseDirectory) {
        String normalized = expandHome(rawPath == null || rawPath.isBlank() ? "." : rawPath.trim());
        Path path = Path.of(normalized);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        Path base = baseDirectory == null
                ? Path.of(".").toAbsolutePath().normalize()
                : baseDirectory.toAbsolutePath().normalize();
        return base.resolve(path).toAbsolutePath().normalize();
    }

    public static Path resolveInAgentWorkspace(String rawPath, ToolRequest request) {
        return resolve(rawPath, agentWorkspace(request));
    }

    public static Path agentWorkspace(ToolRequest request) {
        Path workspace = request.agentWorkspacePath();
        if (workspace != null) {
            return workspace.toAbsolutePath().normalize();
        }
        return NomoClawPaths.agentWorkspace(request.agentName());
    }

    public static String expandHome(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        String home = System.getProperty("user.home");
        if ("~".equals(rawPath)) {
            return home;
        }
        if (rawPath.startsWith("~/")) {
            return home + rawPath.substring(1);
        }
        return rawPath;
    }
}
