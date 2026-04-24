package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.workspace.NomoClawWorkspaceBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NomoClawWorkspaceBootstrapTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateDefaultWorkspaceOnBootstrap() throws Exception {
        Path bundledSkillsRoot = tempDir.resolve("seed-skills");
        createSkill(bundledSkillsRoot, "pdf", "pdf");
        createSkill(bundledSkillsRoot, "xlsx", "xlsx");

        NomoClawWorkspaceBootstrap.bootstrap(tempDir, bundledSkillsRoot);

        Path defaultAgent = tempDir.resolve("agents").resolve("default_agent");
        Path skillsRoot = tempDir.resolve("skills");
        assertTrue(Files.isDirectory(defaultAgent));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("AGENT.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("SOUL.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("IDENTITY.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("USER.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("TOOLS.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("MEMORY.md")));
        assertTrue(Files.isDirectory(defaultAgent.resolve("tmp")));
        assertTrue(Files.isDirectory(defaultAgent.resolve("report")));
        assertTrue(Files.isRegularFile(skillsRoot.resolve("pdf").resolve("SKILL.md")));
        assertTrue(Files.isRegularFile(skillsRoot.resolve("xlsx").resolve("SKILL.md")));
    }

    @Test
    void shouldNotOverrideExistingSkillDirectoryOnBootstrap() throws Exception {
        Path bundledSkillsRoot = tempDir.resolve("seed-skills");
        createSkill(bundledSkillsRoot, "pdf", "pdf from source");

        Path existingSkill = tempDir.resolve("skills").resolve("pdf");
        Files.createDirectories(existingSkill);
        Files.writeString(existingSkill.resolve("SKILL.md"), "existing", StandardCharsets.UTF_8);

        NomoClawWorkspaceBootstrap.bootstrap(tempDir, bundledSkillsRoot);

        String content = Files.readString(existingSkill.resolve("SKILL.md"), StandardCharsets.UTF_8);
        assertEquals("existing", content);
    }

    @Test
    void shouldSkipBundledSkillInitializationAfterSentinelExists() throws Exception {
        Path bundledSkillsRoot = tempDir.resolve("seed-skills");
        createSkill(bundledSkillsRoot, "pdf", "pdf");

        NomoClawWorkspaceBootstrap.bootstrap(tempDir, bundledSkillsRoot);
        assertTrue(Files.isRegularFile(tempDir.resolve("skills").resolve(".bootstrap.done")));

        Path shouldNotCopy = tempDir.resolve("new-source");
        createSkill(shouldNotCopy, "docx", "docx");
        NomoClawWorkspaceBootstrap.bootstrap(tempDir, shouldNotCopy);

        assertFalse(Files.exists(tempDir.resolve("skills").resolve("docx")));
    }

    private static void createSkill(Path skillsRoot, String skillKey, String skillName) throws Exception {
        Path skillDir = skillsRoot.resolve(skillKey);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: %s
                description: test skill
                ---
                """.formatted(skillName), StandardCharsets.UTF_8);
    }
}
