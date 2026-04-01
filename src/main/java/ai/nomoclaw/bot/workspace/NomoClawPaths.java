package ai.nomoclaw.bot.workspace;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NomoClawPaths {

    public static final String ROOT_DIR_NAME = ".nomoclaw";
    public static final String AGENTS_DIR_NAME = "agents";
    public static final String SKILLS_DIR_NAME = "skills";
    public static final String DEFAULT_AGENT_NAME = "default";
    public static final String TMP_DIR_NAME = "tmp";
    public static final String REPORT_DIR_NAME = "report";
    private static volatile Path configuredRoot = defaultRoot();

    private NomoClawPaths() {
    }

    private static Path defaultRoot() {
        return Path.of(System.getProperty("user.home"), ROOT_DIR_NAME).toAbsolutePath().normalize();
    }

    public static void configureRoot(Path rootPath) {
        configuredRoot = normalizeRoot(rootPath);
    }

    private static Path normalizeRoot(Path rootPath) {
        Path candidate = rootPath == null ? defaultRoot() : rootPath;
        return candidate.toAbsolutePath().normalize();
    }

    public static Path root() {
        return configuredRoot;
    }

    public static Path agentsRoot() {
        return root().resolve(AGENTS_DIR_NAME).toAbsolutePath().normalize();
    }

    public static Path skillsRoot() {
        return root().resolve(SKILLS_DIR_NAME).toAbsolutePath().normalize();
    }

    public static Path agentWorkspace(String agentName) {
        String normalizedName = (agentName == null || agentName.isBlank()) ? DEFAULT_AGENT_NAME : agentName.trim();
        return agentsRoot().resolve(normalizedName).toAbsolutePath().normalize();
    }

    public static Path agentTmp(String agentName) {
        return agentWorkspace(agentName).resolve(TMP_DIR_NAME).toAbsolutePath().normalize();
    }

    public static Path agentReport(String agentName) {
        return agentWorkspace(agentName).resolve(REPORT_DIR_NAME).toAbsolutePath().normalize();
    }

    public static Path agentTmp(Path agentWorkspace) {
        return agentWorkspace.toAbsolutePath().normalize().resolve(TMP_DIR_NAME).toAbsolutePath().normalize();
    }

    public static Path agentReport(Path agentWorkspace) {
        return agentWorkspace.toAbsolutePath().normalize().resolve(REPORT_DIR_NAME).toAbsolutePath().normalize();
    }

    public static Path ensureAgentWorkspace(String agentName) {
        return ensureAgentWorkspace(agentWorkspace(agentName));
    }

    public static Path ensureAgentWorkspace(Path agentWorkspace) {
        try {
            Path normalized = agentWorkspace.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            Files.createDirectories(agentTmp(normalized));
            Files.createDirectories(agentReport(normalized));
            return normalized;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize agent workspace: " + agentWorkspace, ex);
        }
    }
}
