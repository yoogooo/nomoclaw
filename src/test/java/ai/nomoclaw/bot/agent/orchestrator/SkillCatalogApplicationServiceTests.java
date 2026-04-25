package ai.nomoclaw.bot.agent.orchestrator;

import ai.nomoclaw.bot.application.dto.GlobalSkillDto;
import ai.nomoclaw.bot.orchestrator.SkillCatalogApplicationService;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SkillCatalogApplicationServiceTests {

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
    void shouldListGlobalSkillsWithLinkedAgents() {
        SkillDefinitionRepository skillRepo = mock(SkillDefinitionRepository.class);
        AgentSkillRelationRepository relationRepo = mock(AgentSkillRelationRepository.class);
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        SkillCatalogApplicationService service = new SkillCatalogApplicationService(skillRepo, relationRepo, agentRepo);

        doReturn(List.of(skill("skill-a", "ACTIVE"), skill("skill-b", "DISABLED"))).when(skillRepo).listAll();
        doReturn(List.of(relation("agent-1", "skill-a"), relation("agent-2", "skill-b"))).when(relationRepo)
                .listActiveBySkillKeys(List.of("skill-a", "skill-b"));
        doReturn(List.of(agent("agent-1", "Boss 分身"), agent("agent-2", "老板"))).when(agentRepo)
                .listActiveByUids(anyCollection());

        List<GlobalSkillDto> skills = service.listSkills();

        assertEquals(2, skills.size());
        assertEquals("skill-a", skills.get(0).skillKey());
        assertEquals(1, skills.get(0).linkedAgents().size());
        assertEquals("Boss 分身", skills.get(0).linkedAgents().get(0).displayName());
        assertEquals("DISABLED", skills.get(1).status());
        assertEquals("老板", skills.get(1).linkedAgents().get(0).displayName());
    }

    @Test
    void shouldUpdateGlobalSkillStatus() {
        SkillDefinitionRepository skillRepo = mock(SkillDefinitionRepository.class);
        AgentSkillRelationRepository relationRepo = mock(AgentSkillRelationRepository.class);
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        SkillCatalogApplicationService service = new SkillCatalogApplicationService(skillRepo, relationRepo, agentRepo);
        SkillDefinitionEntity existing = skill("skill-a", "ACTIVE");
        doReturn(existing).when(skillRepo).findByKey("skill-a");
        doReturn(List.of(relation("agent-1", "skill-a"))).when(relationRepo).listActiveBySkillKeys(List.of("skill-a"));
        doReturn(List.of(agent("agent-1", "Boss 分身"))).when(agentRepo).listActiveByUids(anyCollection());

        GlobalSkillDto updated = service.updateSkillStatus("skill-a", false);

        ArgumentCaptor<SkillDefinitionEntity> captor = ArgumentCaptor.forClass(SkillDefinitionEntity.class);
        verify(skillRepo).updateById(captor.capture());
        assertEquals("DISABLED", captor.getValue().getStatus());
        assertEquals("DISABLED", updated.status());
        assertEquals(1, updated.linkedAgents().size());
    }

    @Test
    void shouldDeleteGlobalSkillAndRemoveDirectory() throws Exception {
        tempRoot = Files.createTempDirectory("skill-catalog-test-");
        NomoClawPaths.configureRoot(tempRoot);
        Path skillDir = NomoClawPaths.skillsRoot().resolve("skill-a");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "test");

        SkillDefinitionRepository skillRepo = mock(SkillDefinitionRepository.class);
        AgentSkillRelationRepository relationRepo = mock(AgentSkillRelationRepository.class);
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        SkillCatalogApplicationService service = new SkillCatalogApplicationService(skillRepo, relationRepo, agentRepo);
        doReturn(skill("skill-a", "ACTIVE")).when(skillRepo).findByKey("skill-a");

        service.deleteSkill("skill-a");

        verify(relationRepo).deleteBySkillKey("skill-a");
        verify(skillRepo).deleteBySkillKey("skill-a");
        assertFalse(Files.exists(skillDir));
    }

    @Test
    void shouldIgnoreMissingDirectoryWhenDeletingSkill() throws Exception {
        tempRoot = Files.createTempDirectory("skill-catalog-test-");
        NomoClawPaths.configureRoot(tempRoot);
        SkillDefinitionRepository skillRepo = mock(SkillDefinitionRepository.class);
        AgentSkillRelationRepository relationRepo = mock(AgentSkillRelationRepository.class);
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        SkillCatalogApplicationService service = new SkillCatalogApplicationService(skillRepo, relationRepo, agentRepo);
        doReturn(skill("skill-a", "ACTIVE")).when(skillRepo).findByKey("skill-a");

        service.deleteSkill("skill-a");

        verify(relationRepo).deleteBySkillKey("skill-a");
        verify(skillRepo).deleteBySkillKey("skill-a");
    }

    @Test
    void shouldRejectMissingSkillWhenUpdatingOrDeleting() {
        SkillDefinitionRepository skillRepo = mock(SkillDefinitionRepository.class);
        AgentSkillRelationRepository relationRepo = mock(AgentSkillRelationRepository.class);
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        SkillCatalogApplicationService service = new SkillCatalogApplicationService(skillRepo, relationRepo, agentRepo);

        assertThrows(IllegalArgumentException.class, () -> service.updateSkillStatus("missing", true));
        assertThrows(IllegalArgumentException.class, () -> service.deleteSkill("missing"));
    }

    private SkillDefinitionEntity skill(String key, String status) {
        SkillDefinitionEntity entity = new SkillDefinitionEntity();
        entity.setSkillKey(key);
        entity.setDisplayName(key);
        entity.setDescription(key + "-desc");
        entity.setSkillPath(NomoClawPaths.skillsRoot().resolve(key).toString());
        entity.setStatus(status);
        entity.setUpdatedTime(LocalDateTime.now());
        return entity;
    }

    private AgentSkillRelationEntity relation(String agentUid, String skillKey) {
        AgentSkillRelationEntity entity = new AgentSkillRelationEntity();
        entity.setAgentUid(agentUid);
        entity.setSkillKey(skillKey);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private AgentDefinitionEntity agent(String uid, String displayName) {
        AgentDefinitionEntity entity = new AgentDefinitionEntity();
        entity.setAgentUid(uid);
        entity.setAgentName(uid);
        entity.setDisplayName(displayName);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
