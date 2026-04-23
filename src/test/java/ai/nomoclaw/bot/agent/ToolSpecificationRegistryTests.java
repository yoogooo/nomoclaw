package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.orchestrator.ToolSpecificationRegistry;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentToolRelationEntity;
import ai.nomoclaw.bot.store.entity.ToolDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentToolRelationRepository;
import ai.nomoclaw.bot.store.repository.ToolDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolSpecificationRegistryTests {

    @Test
    void shouldFilterToolsByAgentAuthorization() {
        AgentDefinitionRepository agentDefinitionRepository = mock(AgentDefinitionRepository.class);
        ToolDefinitionRepository toolDefinitionRepository = mock(ToolDefinitionRepository.class);
        AgentToolRelationRepository agentToolRelationRepository = mock(AgentToolRelationRepository.class);

        AgentDefinitionEntity agent = new AgentDefinitionEntity();
        agent.setAgentUid("agent_demo");
        agent.setAgentName("demo");
        when(agentDefinitionRepository.findActiveByName("demo")).thenReturn(agent);

        AgentToolRelationEntity relation = new AgentToolRelationEntity();
        relation.setToolKey("command_tool");
        when(agentToolRelationRepository.listActiveByAgentUid("agent_demo")).thenReturn(List.of(relation));

        ToolDefinitionEntity tool = new ToolDefinitionEntity();
        tool.setToolKey("command_tool");
        when(toolDefinitionRepository.listActiveByKeys(List.of("command_tool"))).thenReturn(List.of(tool));

        ToolSpecificationRegistry registry = new ToolSpecificationRegistry(
                agentDefinitionRepository,
                toolDefinitionRepository,
                agentToolRelationRepository
        );

        List<String> toolNames = registry.listForAgent("demo").stream().map(spec -> spec.name()).toList();
        assertEquals(List.of("command_tool"), toolNames);
        assertTrue(registry.isToolAllowed("demo", "command_tool"));
        assertFalse(registry.isToolAllowed("demo", "cron_tool"));
    }

    @Test
    void shouldFallbackToAllToolsWhenAgentHasNoRelations() {
        AgentDefinitionRepository agentDefinitionRepository = mock(AgentDefinitionRepository.class);
        ToolDefinitionRepository toolDefinitionRepository = mock(ToolDefinitionRepository.class);
        AgentToolRelationRepository agentToolRelationRepository = mock(AgentToolRelationRepository.class);

        ToolSpecificationRegistry registry = new ToolSpecificationRegistry(
                agentDefinitionRepository,
                toolDefinitionRepository,
                agentToolRelationRepository
        );

        assertTrue(registry.listForAgent("unknown").size() > 3);
        assertTrue(registry.isToolAllowed("unknown", "cron_tool"));
        assertTrue(registry.isToolAllowed("unknown", "image_loader_tool"));
    }
}
