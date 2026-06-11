package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.orchestrator.ToolSpecificationRegistry;
import ai.nomoclaw.bot.mcp.McpApplicationService;
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
        McpApplicationService mcpApplicationService = mock(McpApplicationService.class);
        when(mcpApplicationService.listActiveToolSnapshots()).thenReturn(List.of());

        AgentDefinitionEntity agent = new AgentDefinitionEntity();
        agent.setAgentUid("agent_demo");
        agent.setAgentName("demo");
        when(agentDefinitionRepository.findActiveByName("demo")).thenReturn(agent);

        AgentToolRelationEntity relation = new AgentToolRelationEntity();
        relation.setToolKey("CommandTool");
        when(agentToolRelationRepository.listActiveByAgentUid("agent_demo")).thenReturn(List.of(relation));

        ToolDefinitionEntity tool = new ToolDefinitionEntity();
        tool.setToolKey("CommandTool");
        when(toolDefinitionRepository.listActiveByKeys(List.of("CommandTool"))).thenReturn(List.of(tool));

        ToolSpecificationRegistry registry = new ToolSpecificationRegistry(
                agentDefinitionRepository,
                toolDefinitionRepository,
                agentToolRelationRepository,
                mcpApplicationService
        );

        List<String> toolNames = registry.listForAgent("demo").stream().map(spec -> spec.name()).toList();
        assertEquals(List.of("CommandTool"), toolNames);
        assertTrue(registry.isToolAllowed("demo", "CommandTool"));
        assertFalse(registry.isToolAllowed("demo", "CronCreateTool"));
    }

    @Test
    void shouldExcludeDisabledToolsFromAgentToolSpecifications() {
        AgentDefinitionRepository agentDefinitionRepository = mock(AgentDefinitionRepository.class);
        ToolDefinitionRepository toolDefinitionRepository = mock(ToolDefinitionRepository.class);
        AgentToolRelationRepository agentToolRelationRepository = mock(AgentToolRelationRepository.class);
        McpApplicationService mcpApplicationService = mock(McpApplicationService.class);
        when(mcpApplicationService.listActiveToolSnapshots()).thenReturn(List.of());

        AgentDefinitionEntity agent = new AgentDefinitionEntity();
        agent.setAgentUid("agent_demo");
        agent.setAgentName("demo");
        when(agentDefinitionRepository.findActiveByName("demo")).thenReturn(agent);

        AgentToolRelationEntity enabledRelation = new AgentToolRelationEntity();
        enabledRelation.setToolKey("CommandTool");
        AgentToolRelationEntity disabledRelation = new AgentToolRelationEntity();
        disabledRelation.setToolKey("BrowserTool");
        when(agentToolRelationRepository.listActiveByAgentUid("agent_demo")).thenReturn(List.of(enabledRelation));

        ToolDefinitionEntity commandTool = new ToolDefinitionEntity();
        commandTool.setToolKey("CommandTool");
        when(toolDefinitionRepository.listActiveByKeys(List.of("CommandTool"))).thenReturn(List.of(commandTool));

        ToolSpecificationRegistry registry = new ToolSpecificationRegistry(
                agentDefinitionRepository,
                toolDefinitionRepository,
                agentToolRelationRepository,
                mcpApplicationService
        );

        List<String> toolNames = registry.listForAgent("demo").stream().map(spec -> spec.name()).toList();
        assertEquals(List.of("CommandTool"), toolNames);
        assertFalse(toolNames.contains("BrowserTool"));
        assertFalse(registry.isToolAllowed("demo", "BrowserTool"));
    }

    @Test
    void shouldFallbackToAllToolsWhenAgentHasNoRelations() {
        AgentDefinitionRepository agentDefinitionRepository = mock(AgentDefinitionRepository.class);
        ToolDefinitionRepository toolDefinitionRepository = mock(ToolDefinitionRepository.class);
        AgentToolRelationRepository agentToolRelationRepository = mock(AgentToolRelationRepository.class);
        McpApplicationService mcpApplicationService = mock(McpApplicationService.class);
        when(mcpApplicationService.listActiveToolSnapshots()).thenReturn(List.of());

        ToolSpecificationRegistry registry = new ToolSpecificationRegistry(
                agentDefinitionRepository,
                toolDefinitionRepository,
                agentToolRelationRepository,
                mcpApplicationService
        );

        assertTrue(registry.listForAgent("unknown").size() > 3);
        List<String> toolNames = registry.listForAgent("unknown").stream().map(spec -> spec.name()).toList();
        assertTrue(toolNames.contains("CronCreateTool"));
        assertTrue(toolNames.contains("CronDeleteTool"));
        assertTrue(toolNames.contains("CronListTool"));
        assertTrue(toolNames.contains("ReadFileTool"));
        assertTrue(toolNames.contains("ListFileTool"));
        assertTrue(toolNames.contains("CreateFileTool"));
        assertTrue(toolNames.contains("EditFileTool"));
        assertTrue(toolNames.contains("WebSearchTool"));
        assertTrue(toolNames.contains("WebFetchTool"));
        assertFalse(toolNames.contains("FileTool"));
        assertTrue(registry.isToolAllowed("unknown", "CronCreateTool"));
        assertTrue(registry.isToolAllowed("unknown", "CronDeleteTool"));
        assertTrue(registry.isToolAllowed("unknown", "CronListTool"));
        assertTrue(registry.isToolAllowed("unknown", "ImageLoaderTool"));
        assertFalse(registry.isToolAllowed("unknown", "FileTool"));
    }
}
