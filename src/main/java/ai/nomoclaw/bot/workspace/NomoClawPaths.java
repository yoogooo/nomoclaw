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
    public static final String RUNTIME_DIR_NAME = "runtime";
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

    public static Path runtimeRoot() {
        return root().resolve(RUNTIME_DIR_NAME).toAbsolutePath().normalize();
    }

    public static Path runtimeLogsRoot() {
        return runtimeRoot().resolve("logs").toAbsolutePath().normalize();
    }

    public static Path runtimeBrowserProfilesRoot() {
        return runtimeRoot().resolve("browser-profiles").toAbsolutePath().normalize();
    }

    public static Path runtimePlaywrightBrowsersRoot() {
        return runtimeRoot().resolve("playwright-browsers").toAbsolutePath().normalize();
    }

    public static Path runtimePluginsRoot() {
        return runtimeRoot().resolve("plugins").toAbsolutePath().normalize();
    }

    public static Path runtimeTmpRoot() {
        return runtimeRoot().resolve(TMP_DIR_NAME).toAbsolutePath().normalize();
    }

    public static Path ensureRuntimeRoot() {
        return ensureDirectory(runtimeRoot(), "runtime root");
    }

    public static Path ensureRuntimeLogsRoot() {
        return ensureDirectory(runtimeLogsRoot(), "runtime logs root");
    }

    public static Path ensureRuntimeBrowserProfilesRoot() {
        return ensureDirectory(runtimeBrowserProfilesRoot(), "runtime browser profiles root");
    }

    public static Path ensureRuntimePlaywrightBrowsersRoot() {
        return ensureDirectory(runtimePlaywrightBrowsersRoot(), "runtime playwright browsers root");
    }

    public static Path ensureRuntimePluginsRoot() {
        return ensureDirectory(runtimePluginsRoot(), "runtime plugins root");
    }

    public static Path ensureRuntimeTmpRoot() {
        return ensureDirectory(runtimeTmpRoot(), "runtime tmp root");
    }

    public static Path agentHome(String agentName) {
        String normalizedName = (agentName == null || agentName.isBlank()) ? DEFAULT_AGENT_NAME : agentName.trim();
        return agentsRoot().resolve(normalizedName).toAbsolutePath().normalize();
    }

    public static Path agentWorkspace(String agentName) {
        return agentHome(agentName);
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

    public static Path ensureAgentHome(String agentName) {
        return ensureAgentHome(agentHome(agentName));
    }

    public static Path ensureAgentHome(Path agentHome) {
        return ensureDirectory(agentHome, "agent home");
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

    private static Path ensureDirectory(Path path, String label) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            return normalized;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize " + label + ": " + path, ex);
        }
    }
}
