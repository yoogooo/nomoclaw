package ai.nomoclaw.bot.prompt;

import ai.nomoclaw.bot.workspace.NomoClawPaths;
import ai.nomoclaw.bot.util.CommandShellResolver;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class PromptLoader {

    public static final String SYSTEM_PROMPT_PATH = "src/main/resources/prompts/AGENTS.md";
    public static final String SYSTEM_PROMPT_CLASSPATH = "prompts/AGENTS.md";

    private static final String[] PROMPT_FILES = {
            "AGENT.md",
            "SOUL.md",
            "IDENTITY.md",
            "USER.md",
            "TOOLS.md",
            "MEMORY.md"
    };
    private static final String CURRENT_OS_CONTEXT = detectCurrentOsContext();

    private PromptLoader() {
    }

    public static String loadSystemPrompt(String fallback) {
        String fromFileSystem = readPromptFile(Path.of(SYSTEM_PROMPT_PATH).toAbsolutePath().normalize());
        if (fromFileSystem != null) {
            return fromFileSystem;
        }
        String fromClasspath = readClasspathPrompt();
        if (fromClasspath != null) {
            return fromClasspath;
        }
        return fallback;
    }

    public static String buildSystemPrompt(PromptContext context,
                                           String builtinToolsPrompt,
                                           String toolkitPrompt,
                                           String fallback) {
        StringBuilder builder = new StringBuilder();

        Path promptProfilePath = context.agentProfilePath() == null ? context.agentWorkspacePath() : context.agentProfilePath();
        Map<String, String> sections = new LinkedHashMap<>();
        if (promptProfilePath != null) {
            for (String fileName : PROMPT_FILES) {
                sections.put(fileName, readPromptFile(promptProfilePath.resolve(fileName)));
            }
        }

        boolean hasAnySection = false;
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            hasAnySection = true;
            builder.append("# ").append(entry.getKey()).append('\n');
            builder.append(entry.getValue().trim()).append("\n\n");
        }

        if (!hasAnySection) {
            String defaultPrompt = loadSystemPrompt(fallback);
            if (defaultPrompt != null && !defaultPrompt.isBlank()) {
                builder.append("# DEFAULT_SYSTEM_PROMPT\n");
                builder.append(defaultPrompt.trim()).append("\n\n");
            }
        }

        builder.append("# ENVIRONMENT\n");
        builder.append(renderEnvContext(context)).append("\n\n");

        builder.append("[内置工具]\n\n");
        if (builtinToolsPrompt != null && !builtinToolsPrompt.isBlank()) {
            builder.append(builtinToolsPrompt.trim()).append("\n\n");
        } else {
            builder.append("(none)\n\n");
        }

        if (toolkitPrompt != null && !toolkitPrompt.isBlank()) {
            builder.append(toolkitPrompt.trim()).append("\n\n");
        }
        return builder.toString().trim();
    }

    private static String renderEnvContext(PromptContext context) {
        ZoneId localZone = ZoneId.systemDefault();
        return """
                ====================
                - %s
                - 工作目录: %s
                - 当前 agent 工作区: %s
                - 当前 agent 临时目录: %s
                - 当前 agent 交付目录: %s
                - 当前时区: %s
                - 重要提示:
                  1. 优先考虑使用 skills
                  2. 写文件前先 read_file，必要时 edit_file
                  3. 所有相对路径都相对当前 agent 工作区解析
                  4. 截图、下载、中间素材、临时文件统一放 tmp/
                  5. 最终交付给用户的成品统一放 report/
                  6. 需要发送文件时，优先发送 report/ 下的文件
                ====================
                """.formatted(
                CURRENT_OS_CONTEXT,
                context.workingDirectory().toAbsolutePath().normalize(),
                context.agentWorkspacePath() == null ? "" : context.agentWorkspacePath().toAbsolutePath().normalize(),
                context.agentTmpPath() == null ? "" : context.agentTmpPath().toAbsolutePath().normalize(),
                context.agentReportPath() == null ? "" : context.agentReportPath().toAbsolutePath().normalize(),
                localZone.getId()
        ).trim();
    }

    private static String detectCurrentOsContext() {
        String osName = System.getProperty("os.name", "").trim();
        String osVersion = System.getProperty("os.version", "").trim();
        String osArch = System.getProperty("os.arch", "").trim();
        OsInfo osInfo = osName.toLowerCase().contains("mac")
                ? detectMacOs(osName, osVersion)
                : new OsInfo(normalizeBlank(osName), osVersion);
        String shell = CommandShellResolver.resolve().displayName();
        StringBuilder builder = new StringBuilder("Current OS: ");
        builder.append(osInfo.name());
        if (!osInfo.version().isBlank()) {
            builder.append(' ').append(osInfo.version());
        }
        if (!osArch.isBlank()) {
            builder.append(" (").append(osArch).append(')');
        }
        builder.append(", Shell: ").append(shell);
        return builder.toString().trim();
    }

    private static OsInfo detectMacOs(String fallbackName, String fallbackVersion) {
        Map<String, String> swVers = readKeyValueCommand("sw_vers");
        String productName = swVers.getOrDefault("ProductName", fallbackName.isBlank() ? "macOS" : fallbackName);
        String productVersion = swVers.getOrDefault("ProductVersion", fallbackVersion);
        String codeName = macOsCodeName(productVersion);
        String displayName = codeName.isBlank()
                ? productName
                : productName + " " + codeName;
        return new OsInfo(displayName, productVersion);
    }

    private record OsInfo(String name, String version) {
        OsInfo {
            name = normalizeBlank(name);
            version = version == null ? "" : version.trim();
        }
    }

    private static Map<String, String> readKeyValueCommand(String... command) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            Process process = new ProcessBuilder(command).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int separatorIndex = line.indexOf(':');
                    if (separatorIndex <= 0) {
                        continue;
                    }
                    String key = line.substring(0, separatorIndex).trim();
                    String value = line.substring(separatorIndex + 1).trim();
                    values.put(key, value);
                }
            }
            process.waitFor();
        } catch (Exception ex) {
            log.debug("[PromptLoader] failed to read command output command={} err={}", List.of(command), ex.toString());
        }
        return values;
    }

    private static String macOsCodeName(String version) {
        if (version == null || version.isBlank()) {
            return "";
        }
        String major = version.split("\\.")[0];
        return switch (major) {
            case "26" -> "Tahoe";
            case "15" -> "Sequoia";
            case "14" -> "Sonoma";
            case "13" -> "Ventura";
            case "12" -> "Monterey";
            case "11" -> "Big Sur";
            default -> "";
        };
    }

    private static String normalizeBlank(String text) {
        return text == null || text.isBlank() ? "Unknown" : text.trim();
    }

    private static String readPromptFile(Path path) {
        try {
            if (!Files.exists(path)) {
                return null;
            }
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.isBlank()) {
                log.warn("[PromptLoader] prompt file is blank path={}", path);
                return null;
            }
            return content;
        } catch (Exception e) {
            log.warn("[PromptLoader] failed to read prompt from filesystem path={} err={}", path, e.toString());
            return null;
        }
    }

    private static String readClasspathPrompt() {
        try (InputStream inputStream = PromptLoader.class.getClassLoader()
                .getResourceAsStream(SYSTEM_PROMPT_CLASSPATH)) {
            if (inputStream == null) {
                return null;
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            return content.isBlank() ? null : content;
        } catch (Exception e) {
            log.warn("[PromptLoader] failed to read prompt from classpath={} err={}", SYSTEM_PROMPT_CLASSPATH, e.toString());
            return null;
        }
    }

    public record PromptContext(
            String sessionId,
            String messageUid,
            String userId,
            String channel,
            Path workingDirectory,
            String agentGroup,
            String agentName,
            Path agentProfilePath,
            Path agentWorkspacePath,
            Path agentTmpPath,
            Path agentReportPath
    ) {
        public PromptContext {
            workingDirectory = workingDirectory == null
                    ? NomoClawPaths.root()
                    : workingDirectory.toAbsolutePath().normalize();
            Path defaultAgentHome = null;
            if (agentName != null && !agentName.isBlank()) {
                defaultAgentHome = workingDirectory.resolve(NomoClawPaths.AGENTS_DIR_NAME).resolve(agentName).toAbsolutePath().normalize();
            }
            agentProfilePath = agentProfilePath == null ? normalize(defaultAgentHome) : normalize(agentProfilePath);
            if (agentProfilePath != null) {
                agentProfilePath = ensureDirectory(agentProfilePath);
            }

            agentWorkspacePath = agentWorkspacePath == null ? normalize(agentProfilePath) : normalize(agentWorkspacePath);
            if (agentWorkspacePath != null) {
                agentWorkspacePath = ensureDirectory(agentWorkspacePath);
            }

            agentTmpPath = agentTmpPath == null && agentWorkspacePath != null
                    ? normalize(NomoClawPaths.agentTmp(agentWorkspacePath))
                    : normalize(agentTmpPath);
            if (agentTmpPath != null) {
                agentTmpPath = ensureDirectory(agentTmpPath);
            }

            agentReportPath = agentReportPath == null && agentWorkspacePath != null
                    ? normalize(NomoClawPaths.agentReport(agentWorkspacePath))
                    : normalize(agentReportPath);
            if (agentReportPath != null) {
                agentReportPath = ensureDirectory(agentReportPath);
            }
        }

        public static PromptContext defaultFor(String sessionId, String channel) {
            return new PromptContext(sessionId, "", "local-user", channel, NomoClawPaths.root(), "", "", null, null, null, null);
        }

        public static PromptContext forAgent(String sessionId,
                                             String messageUid,
                                             String channel,
                                             String agentGroup,
                                             String agentName,
                                             Path workingDirectory) {
            return forAgent(sessionId, messageUid, channel, agentGroup, agentName, workingDirectory, null, null, null, null);
        }

        public static PromptContext forAgent(String sessionId,
                                             String messageUid,
                                             String channel,
                                             String agentGroup,
                                             String agentName,
                                             Path workingDirectory,
                                             Path agentProfilePath,
                                             Path agentWorkspacePath,
                                             Path agentTmpPath,
                                             Path agentReportPath) {
            Path basePath = workingDirectory == null ? NomoClawPaths.root() : workingDirectory;
            return new PromptContext(
                    sessionId,
                    messageUid,
                    "local-user",
                    channel,
                    basePath,
                    agentGroup,
                    agentName,
                    agentProfilePath,
                    agentWorkspacePath,
                    agentTmpPath,
                    agentReportPath
            );
        }

        private static Path normalize(Path path) {
            return path == null ? null : path.toAbsolutePath().normalize();
        }

        private static Path ensureDirectory(Path path) {
            try {
                Path normalized = path.toAbsolutePath().normalize();
                Files.createDirectories(normalized);
                return normalized;
            } catch (Exception ex) {
                throw new IllegalStateException("failed to initialize prompt context path: " + path, ex);
            }
        }
    }
}
