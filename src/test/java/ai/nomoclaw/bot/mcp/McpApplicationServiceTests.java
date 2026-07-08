package ai.nomoclaw.bot.mcp;

import ai.nomoclaw.bot.orchestrator.SystemErrorLogService;
import ai.nomoclaw.bot.store.entity.AgentMcpToolRelationEntity;
import ai.nomoclaw.bot.store.entity.McpToolSnapshotEntity;
import ai.nomoclaw.bot.store.repository.AgentMcpToolRelationRepository;
import ai.nomoclaw.bot.store.repository.McpServerDefinitionRepository;
import ai.nomoclaw.bot.store.repository.McpToolSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpApplicationServiceTests {

    @Test
    void shouldInitializeAgentMcpToolRelationsAsActive() {
        McpServerDefinitionRepository serverRepository = mock(McpServerDefinitionRepository.class);
        McpToolSnapshotRepository toolSnapshotRepository = mock(McpToolSnapshotRepository.class);
        AgentMcpToolRelationRepository agentMcpToolRelationRepository = mock(AgentMcpToolRelationRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpToolKeyGenerator toolKeyGenerator = mock(McpToolKeyGenerator.class);
        SystemErrorLogService systemErrorLogService = mock(SystemErrorLogService.class);
        CodexAppServerBridge codexAppServerBridge = mock(CodexAppServerBridge.class);

        McpToolSnapshotEntity firstTool = new McpToolSnapshotEntity();
        firstTool.setToolKey("mcp_maps_text_search");
        McpToolSnapshotEntity secondTool = new McpToolSnapshotEntity();
        secondTool.setToolKey("mcp_maps_place_search");
        when(toolSnapshotRepository.listActive()).thenReturn(List.of(firstTool, secondTool));

        McpApplicationService service = new McpApplicationService(
                serverRepository,
                toolSnapshotRepository,
                agentMcpToolRelationRepository,
                clientFactory,
                toolKeyGenerator,
                systemErrorLogService,
                codexAppServerBridge
        );

        LocalDateTime now = LocalDateTime.now();
        service.initializeAgentToolRelations("agent_demo", now);

        ArgumentCaptor<AgentMcpToolRelationEntity> relationCaptor = ArgumentCaptor.forClass(AgentMcpToolRelationEntity.class);
        verify(agentMcpToolRelationRepository, times(2)).save(relationCaptor.capture());
        List<AgentMcpToolRelationEntity> relations = relationCaptor.getAllValues();
        assertEquals(List.of("mcp_maps_text_search", "mcp_maps_place_search"),
                relations.stream().map(AgentMcpToolRelationEntity::getToolKey).toList());
        for (AgentMcpToolRelationEntity relation : relations) {
            assertEquals("agent_demo", relation.getAgentUid());
            assertEquals("ACTIVE", relation.getStatus());
            assertEquals("{}", relation.getConfigJson());
            assertEquals(500, relation.getSortIndex());
            assertEquals(now, relation.getCreatedTime());
            assertEquals(now, relation.getUpdatedTime());
            assertNotNull(relation.getRelationUid());
        }
    }
}
