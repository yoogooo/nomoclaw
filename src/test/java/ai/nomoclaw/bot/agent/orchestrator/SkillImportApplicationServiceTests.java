package ai.nomoclaw.bot.agent.orchestrator;

import ai.nomoclaw.bot.application.command.CreateSkillCommand;
import ai.nomoclaw.bot.application.dto.AgentSkillDto;
import ai.nomoclaw.bot.orchestrator.SkillImportApplicationService;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentSkillRelationEntity;
import ai.nomoclaw.bot.store.entity.SkillDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentSkillRelationRepository;
import ai.nomoclaw.bot.store.repository.SkillDefinitionRepository;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SkillImportApplicationServiceTests {

    private Path tempRoot;

    @AfterEach
    void cleanup() throws Exception {
        if (tempRoot != null && Files.exists(tempRoot)) {
            try (var walk = Files.walk(tempRoot)) {
                walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void shouldCreateSkillAndAttachToAgent() throws Exception {
        tempRoot = Files.createTempDirectory("skill-import-test-");
        NomoClawPaths.configureRoot(tempRoot);
        SkillDefinitionRepository skillRepo = mock(SkillDefinitionRepository.class);
        AgentSkillRelationRepository relationRepo = mock(AgentSkillRelationRepository.class);
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        doReturn(agent("agent-1")).when(agentRepo).findByUid("agent-1");
        doReturn(null).when(skillRepo).findByKey("daily-report-writer");
        doReturn(null).when(relationRepo).findByAgentUidAndSkillKey("agent-1", "daily-report-writer");
        SkillImportApplicationService service = new SkillImportApplicationService(skillRepo, relationRepo, agentRepo);

        AgentSkillDto created = service.createSkill("agent-1", new CreateSkillCommand(
                "daily-report-writer",
                "日报写作助手",
                "帮助生成日报",
                "根据输入内容整理成日报，并输出重点摘要。",
                true
        ));

        Path skillFile = NomoClawPaths.skillsRoot().resolve("daily-report-writer").resolve("SKILL.md");
        assertEquals("daily-report-writer", created.skillKey());
        assertTrue(created.enabled());
        assertTrue(Files.isRegularFile(skillFile));
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("name: 日报写作助手"));
        assertTrue(content.contains("description: 帮助生成日报"));

        ArgumentCaptor<AgentSkillRelationEntity> relationCaptor = ArgumentCaptor.forClass(AgentSkillRelationEntity.class);
        verify(relationRepo).save(relationCaptor.capture());
        assertEquals("agent-1", relationCaptor.getValue().getAgentUid());
        assertEquals("daily-report-writer", relationCaptor.getValue().getSkillKey());
    }

    @Test
    void shouldRejectDuplicateSkillKey() throws Exception {
        tempRoot = Files.createTempDirectory("skill-import-test-");
        NomoClawPaths.configureRoot(tempRoot);
        SkillDefinitionRepository skillRepo = mock(SkillDefinitionRepository.class);
        AgentSkillRelationRepository relationRepo = mock(AgentSkillRelationRepository.class);
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        doReturn(agent("agent-1")).when(agentRepo).findByUid("agent-1");
        doReturn(null).when(skillRepo).findByKey("duplicate-skill");
        SkillImportApplicationService service = new SkillImportApplicationService(skillRepo, relationRepo, agentRepo);

        service.createSkill("agent-1", new CreateSkillCommand(
                "duplicate-skill",
                "重复技能",
                "第一次创建",
                "",
                false
        ));
        doReturn(existingSkill("duplicate-skill")).when(skillRepo).findByKey("duplicate-skill");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.createSkill("agent-1", new CreateSkillCommand(
                        "duplicate-skill",
                        "重复技能 2",
                        "第二次创建",
                        "",
                        false
                )));

        assertTrue(error.getMessage().contains("skill already exists"));
    }

    private AgentDefinitionEntity agent(String agentUid) {
        AgentDefinitionEntity entity = new AgentDefinitionEntity();
        entity.setAgentUid(agentUid);
        entity.setAgentName("agent_demo");
        entity.setDisplayName("示例 Agent");
        entity.setStatus("ACTIVE");
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        return entity;
    }

    private SkillDefinitionEntity existingSkill(String skillKey) {
        SkillDefinitionEntity entity = new SkillDefinitionEntity();
        entity.setSkillKey(skillKey);
        entity.setDisplayName("已有技能");
        entity.setDescription("已有描述");
        entity.setStatus("ACTIVE");
        return entity;
    }
}
