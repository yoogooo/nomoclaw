package ai.nomoclaw.bot.workspace;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class NomoClawWorkspaceBootstrap {

    private static final Map<String, String> DEFAULT_AGENT_FILES = buildDefaultAgentFiles();
    private static final String SKILL_FILE = "SKILL.md";
    private static final String DEFAULT_BUNDLED_SKILLS_DIR = "skills";
    private static final String SKILLS_INIT_SENTINEL = ".bootstrap.done";

    private NomoClawWorkspaceBootstrap() {
    }

    public static void bootstrap(Path rootPath) {
        bootstrap(rootPath, resolveBundledSkillsRoot());
    }

    public static void bootstrap(Path rootPath, Path bundledSkillsRoot) {
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        Path agentsRoot = normalizedRoot.resolve(NomoClawPaths.AGENTS_DIR_NAME);
        Path defaultAgentRoot = agentsRoot.resolve(NomoClawPaths.DEFAULT_AGENT_NAME);
        Path skillsRoot = normalizedRoot.resolve(NomoClawPaths.SKILLS_DIR_NAME);
        try {
            NomoClawPaths.ensureAgentWorkspace(defaultAgentRoot);
            for (Map.Entry<String, String> entry : DEFAULT_AGENT_FILES.entrySet()) {
                Path file = defaultAgentRoot.resolve(entry.getKey());
                if (Files.notExists(file)) {
                    Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
                }
            }
            Files.deleteIfExists(defaultAgentRoot.resolve("AGENTS.md"));
            initializeBundledSkills(bundledSkillsRoot, skillsRoot);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize nomoclaw workspace: " + normalizedRoot, ex);
        }
        log.info("[Workspace] initialized root={} defaultAgent={} skillsRoot={}", normalizedRoot, defaultAgentRoot, skillsRoot);
    }

    private static Path resolveBundledSkillsRoot() {
        String userDir = System.getProperty("user.dir", "");
        if (userDir.isBlank()) {
            return null;
        }
        return Path.of(userDir).toAbsolutePath().normalize().resolve(DEFAULT_BUNDLED_SKILLS_DIR);
    }

    private static void initializeBundledSkills(Path sourceSkillsRoot, Path targetSkillsRoot) throws IOException {
        Path sentinel = targetSkillsRoot.resolve(SKILLS_INIT_SENTINEL);
        if (Files.isRegularFile(sentinel)) {
            return;
        }
        // Backward compatibility: old installs may already have copied skills without sentinel.
        // Detect once, mark as done, and avoid repeated source scans on subsequent startups.
        if (hasAnyInitializedSkill(targetSkillsRoot)) {
            markSkillsInitialized(targetSkillsRoot, sentinel);
            return;
        }
        if (sourceSkillsRoot == null || !Files.isDirectory(sourceSkillsRoot)) {
            log.info("[Workspace] bundled skills source not found, skip initialization source={}", sourceSkillsRoot);
            return;
        }
        Files.createDirectories(targetSkillsRoot);
        try (var children = Files.list(sourceSkillsRoot)) {
            children.filter(Files::isDirectory)
                    .forEach(skillDir -> copySkillDirectoryIfNeeded(skillDir, targetSkillsRoot.resolve(skillDir.getFileName())));
        }
        markSkillsInitialized(targetSkillsRoot, sentinel);
    }

    private static void copySkillDirectoryIfNeeded(Path sourceSkillDir, Path targetSkillDir) {
        Path sourceSkillFile = sourceSkillDir.resolve(SKILL_FILE);
        if (!Files.isRegularFile(sourceSkillFile)) {
            return;
        }
        if (Files.exists(targetSkillDir)) {
            return;
        }
        try {
            copyDirectory(sourceSkillDir, targetSkillDir);
            log.info("[Workspace] initialized bundled skill source={} target={}", sourceSkillDir, targetSkillDir);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to initialize bundled skill: " + sourceSkillDir, ex);
        }
    }

    private static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceDir.relativize(dir);
                Files.createDirectories(targetDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceDir.relativize(file);
                Files.copy(file, targetDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean hasAnyInitializedSkill(Path targetSkillsRoot) throws IOException {
        if (!Files.isDirectory(targetSkillsRoot)) {
            return false;
        }
        try (var children = Files.list(targetSkillsRoot)) {
            return children.filter(Files::isDirectory)
                    .anyMatch(skillDir -> Files.isRegularFile(skillDir.resolve(SKILL_FILE)));
        }
    }

    private static void markSkillsInitialized(Path targetSkillsRoot, Path sentinel) throws IOException {
        Files.createDirectories(targetSkillsRoot);
        if (Files.notExists(sentinel)) {
            Files.writeString(sentinel, "initialized", StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> buildDefaultAgentFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("AGENT.md", """
                # AGENT
                ## 目标
                - 负责通用任务处理、基础工具协调和最终答复。
                ## 输出约束
                - 优先给结果，再补必要说明。
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
