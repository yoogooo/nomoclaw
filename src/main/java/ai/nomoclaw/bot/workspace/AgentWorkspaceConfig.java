package ai.nomoclaw.bot.workspace;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent 运行时目录配置：
 * - workspaceDir: 相对路径解析基准目录
 * - reportDir: 固定为 workspaceDir/report
 * - tmpDir: 固定为 workspaceDir/tmp
 */
public record AgentWorkspaceConfig(
        Path workspaceDir,
        Path reportDir,
        Path tmpDir
) {

    public AgentWorkspaceConfig {
        workspaceDir = normalize(workspaceDir);
        reportDir = normalize(reportDir == null ? workspaceDir.resolve("report") : reportDir);
        tmpDir = normalize(tmpDir == null ? workspaceDir.resolve("tmp") : tmpDir);
    }

    public static AgentWorkspaceConfig defaults(String agentName) {
        Path workspace = NomoClawPaths.agentHome(agentName)
                .resolve("workspace")
                .toAbsolutePath()
                .normalize();
        return fromWorkspacePath(workspace);
    }

    public static AgentWorkspaceConfig resolve(String agentName, String workspaceRaw) {
        AgentWorkspaceConfig defaults = defaults(agentName);
        if (workspaceRaw == null || workspaceRaw.trim().isBlank()) {
            return defaults;
        }
        try {
            Path parsed = Path.of(workspaceRaw.trim());
            if (!parsed.isAbsolute()) {
                return defaults;
            }
            return fromWorkspacePath(parsed);
        } catch (Exception ignored) {
            return defaults;
        }
    }

    public static AgentWorkspaceConfig fromWorkspacePath(Path workspacePath) {
        Path workspace = normalize(workspacePath);
        return new AgentWorkspaceConfig(
                workspace,
                workspace.resolve("report"),
                workspace.resolve("tmp")
        );
    }

    public AgentWorkspaceConfig ensureDirectories() {
        try {
            Files.createDirectories(workspaceDir);
            Files.createDirectories(reportDir);
            Files.createDirectories(tmpDir);
            return this;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize agent workspace directories", ex);
        }
    }

    private static Path normalize(Path path) {
        if (path == null) {
            return Path.of(".").toAbsolutePath().normalize();
        }
        return path.toAbsolutePath().normalize();
    }
}
