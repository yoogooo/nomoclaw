package ai.nomoclaw.bot.workspace;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class NomoClawWorkspaceBootstrap {

    private static final Map<String, String> DEFAULT_AGENT_FILES = buildDefaultAgentFiles();

    private NomoClawWorkspaceBootstrap() {
    }

    public static void bootstrap(Path rootPath) {
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        Path agentsRoot = normalizedRoot.resolve(NomoClawPaths.AGENTS_DIR_NAME);
        Path defaultAgentRoot = agentsRoot.resolve(NomoClawPaths.DEFAULT_AGENT_NAME);
        try {
            NomoClawPaths.ensureAgentWorkspace(defaultAgentRoot);
            for (Map.Entry<String, String> entry : DEFAULT_AGENT_FILES.entrySet()) {
                Path file = defaultAgentRoot.resolve(entry.getKey());
                if (Files.notExists(file)) {
                    Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize nomoclaw workspace: " + normalizedRoot, ex);
        }
        log.info("[Workspace] initialized root={} defaultAgent={}", normalizedRoot, defaultAgentRoot);
    }

    private static Map<String, String> buildDefaultAgentFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("AGENTS.md", """
                # Default Agent Rules
                - 这是系统首次启动自动创建的默认 agent。
                - 负责通用任务处理、基础工具协调和最终答复。
                - 优先用简洁、直接、可执行的方式回应用户。
                """);
        files.put("SOUL.md", """
                # Values
                - 重视清晰、稳定和完成度。
                - 能直接回答时不要增加无关操作。
                """);
        files.put("IDENTITY.md", """
                # Identity
                - 角色：默认助手
                - 风格：直接、稳健、协作型
                """);
        files.put("USER.md", """
                # User Preferences
                - 默认使用中文回答。
                - 优先给结果，再补必要说明。
                """);
        files.put("TOOLS.md", """
                # Tool Usage
                - 仅在需要读写文件、执行命令、浏览网页、检索记忆或创建定时任务时调用工具。
                - 工具调用应服务于任务完成，不要机械重复。
                """);
        files.put("MEMORY.md", """
                # Memory
                - 这是默认 agent 的长期记忆文件。
                - 可用于记录用户偏好、项目背景和协作约定。
                """);
        return files;
    }
}
