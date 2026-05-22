package ai.nomoclaw.bot.orchestrator.execution;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.orchestrator.ToolSpecificationRegistry;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentGroupDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentGroupMemberEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupMemberRepository;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionScopeResolverTests {

    @Test
    void shouldResolveRuntimeModelByFallbackOrder() {
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        AgentGroupDefinitionRepository groupRepo = mock(AgentGroupDefinitionRepository.class);
        AgentGroupMemberRepository memberRepo = mock(AgentGroupMemberRepository.class);
        ModelConfigAppService modelConfig = mock(ModelConfigAppService.class);
        ToolSpecificationRegistry toolRegistry = mock(ToolSpecificationRegistry.class);

        AgentDefinitionEntity agent = new AgentDefinitionEntity();
        agent.setAgentUid("agent-a");
        agent.setModelProviderId("openai");
        agent.setModelId("gpt-4.1-mini");
        when(agentRepo.findByUid("agent-a")).thenReturn(agent);

        when(modelConfig.getModelConfig()).thenReturn(new ModelConfigDto(List.of(
                new ModelConfigDto.Provider(
                        "openai", "OpenAI", "openai", false, true, false,
                        "", "", true, "", "", "gpt-4.1-mini",
                        List.of(new ModelConfigDto.Model("gpt-4.1-mini", "", List.of(), false, 0, 0, 0, null, true, ""))
                )
        )));
        when(modelConfig.getAvailableModelConfig()).thenReturn(new ModelConfigDto(List.of()));

        ExecutionScopeResolver resolver = new ExecutionScopeResolver(agentRepo, groupRepo, memberRepo, modelConfig, toolRegistry);
        AgentConversation conversation = conversation("conv-1", "", "agent-a", "web");

        RuntimeModelSelection selection = resolver.resolveForMessageSubmission(conversation, "", "");

        assertEquals("openai", selection.modelProvider());
        assertEquals("gpt-4.1-mini", selection.modelName());
    }

    @Test
    void shouldResolveExecutionAgentWithOwnerPriority() {
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        AgentGroupDefinitionRepository groupRepo = mock(AgentGroupDefinitionRepository.class);
        AgentGroupMemberRepository memberRepo = mock(AgentGroupMemberRepository.class);
        ModelConfigAppService modelConfig = mock(ModelConfigAppService.class);
        ToolSpecificationRegistry toolRegistry = mock(ToolSpecificationRegistry.class);

        AgentDefinitionEntity owner = agent("owner", "owner_agent", "openai", "gpt-4.1-mini");
        AgentGroupDefinitionEntity group = new AgentGroupDefinitionEntity();
        group.setAgentGroupUid("group-1");
        group.setGroupName("group_name");
        group.setOwnerAgentUid("owner");

        ToolSpecification readTool = mock(ToolSpecification.class);
        when(readTool.name()).thenReturn("ReadFileTool");
        when(toolRegistry.listForAgent("owner_agent")).thenReturn(List.of(readTool));

        when(groupRepo.findActiveByUid("group-1")).thenReturn(group);
        when(agentRepo.findActiveByUid("owner")).thenReturn(owner);

        when(modelConfig.getAvailableModelConfig()).thenReturn(new ModelConfigDto(List.of(
                provider("openai", "gpt-4.1-mini")
        )));
        when(modelConfig.getModelConfig()).thenReturn(new ModelConfigDto(List.of(
                provider("openai", "gpt-4.1-mini")
        )));

        ExecutionScopeResolver resolver = new ExecutionScopeResolver(agentRepo, groupRepo, memberRepo, modelConfig, toolRegistry);
        ExecutionScope scope = resolver.resolveForExecution(
                conversation("conv-1", "group-1", "", "web"),
                message("msg-1", "conv-1", "anthropic", "claude-sonnet")
        );

        assertEquals("owner", scope.executionAgent().getAgentUid());
        assertEquals(1, scope.availableTools().size());
        assertEquals("ReadFileTool", scope.availableTools().get(0).name());
        assertEquals("anthropic", scope.runtimeModel().modelProvider());
        assertEquals("claude-sonnet", scope.runtimeModel().modelName());
    }

    @Test
    void shouldFilterCronManagementToolsInCronChannel() {
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        AgentGroupDefinitionRepository groupRepo = mock(AgentGroupDefinitionRepository.class);
        AgentGroupMemberRepository memberRepo = mock(AgentGroupMemberRepository.class);
        ModelConfigAppService modelConfig = mock(ModelConfigAppService.class);
        ToolSpecificationRegistry toolRegistry = mock(ToolSpecificationRegistry.class);

        AgentDefinitionEntity owner = agent("owner", "owner_agent", "openai", "gpt-4.1-mini");
        when(agentRepo.findActiveByUid("owner")).thenReturn(owner);

        ToolSpecification cronCreate = mock(ToolSpecification.class);
        when(cronCreate.name()).thenReturn("CronCreateTool");
        ToolSpecification readTool = mock(ToolSpecification.class);
        when(readTool.name()).thenReturn("ReadFileTool");
        when(toolRegistry.listForAgent("owner_agent")).thenReturn(List.of(cronCreate, readTool));

        when(modelConfig.getAvailableModelConfig()).thenReturn(new ModelConfigDto(List.of(provider("openai", "gpt-4.1-mini"))));
        when(modelConfig.getModelConfig()).thenReturn(new ModelConfigDto(List.of(provider("openai", "gpt-4.1-mini"))));

        ExecutionScopeResolver resolver = new ExecutionScopeResolver(agentRepo, groupRepo, memberRepo, modelConfig, toolRegistry);
        ExecutionScope scope = resolver.resolveForExecution(
                conversation("conv-1", "", "owner", "cron"),
                message("msg-1", "conv-1", "openai", "gpt-4.1-mini")
        );

        assertEquals(1, scope.availableTools().size());
        assertEquals("ReadFileTool", scope.availableTools().get(0).name());
    }

    @Test
    void shouldResolveApprovalScopeWithoutLoadingTools() {
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        AgentGroupDefinitionRepository groupRepo = mock(AgentGroupDefinitionRepository.class);
        AgentGroupMemberRepository memberRepo = mock(AgentGroupMemberRepository.class);
        ModelConfigAppService modelConfig = mock(ModelConfigAppService.class);
        ToolSpecificationRegistry toolRegistry = mock(ToolSpecificationRegistry.class);

        AgentDefinitionEntity owner = agent("owner", "owner_agent", "openai", "gpt-4.1-mini");
        when(agentRepo.findActiveByUid("owner")).thenReturn(owner);

        ExecutionScopeResolver resolver = new ExecutionScopeResolver(agentRepo, groupRepo, memberRepo, modelConfig, toolRegistry);
        ExecutionApprovalScope scope = resolver.resolveForApproval(
                conversation("conv-1", "", "owner", "web"),
                message("msg-1", "conv-1", "openai", "gpt-4.1-mini")
        );

        assertEquals("owner", scope.executionAgent().getAgentUid());
        verify(toolRegistry, never()).listForAgent("owner_agent");
    }

    @Test
    void shouldThrowWhenModelNotFoundUnderProvider() {
        AgentDefinitionRepository agentRepo = mock(AgentDefinitionRepository.class);
        AgentGroupDefinitionRepository groupRepo = mock(AgentGroupDefinitionRepository.class);
        AgentGroupMemberRepository memberRepo = mock(AgentGroupMemberRepository.class);
        ModelConfigAppService modelConfig = mock(ModelConfigAppService.class);
        ToolSpecificationRegistry toolRegistry = mock(ToolSpecificationRegistry.class);

        when(modelConfig.getModelConfig()).thenReturn(new ModelConfigDto(List.of(provider("openai", "gpt-4.1-mini"))));

        ExecutionScopeResolver resolver = new ExecutionScopeResolver(agentRepo, groupRepo, memberRepo, modelConfig, toolRegistry);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.validateModelSelection("openai", List.of("gpt-5")));
        assertEquals("model not found under provider: openai/gpt-5", ex.getMessage());
    }

    private static AgentConversation conversation(String conversationUid, String groupUid, String agentUid, String channel) {
        return new AgentConversation(
                conversationUid,
                groupUid,
                agentUid,
                channel,
                "",
                false,
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );
    }

    private static AgentMessage message(String messageUid, String conversationUid, String provider, String modelName) {
        return new AgentMessage(
                messageUid,
                conversationUid,
                null,
                "user",
                "hello",
                MessageStatus.CREATED,
                provider,
                modelName,
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );
    }

    private static AgentDefinitionEntity agent(String uid, String name, String provider, String modelId) {
        AgentDefinitionEntity entity = new AgentDefinitionEntity();
        entity.setAgentUid(uid);
        entity.setAgentName(name);
        entity.setModelProviderId(provider);
        entity.setModelId(modelId);
        return entity;
    }

    private static ModelConfigDto.Provider provider(String id, String modelId) {
        return new ModelConfigDto.Provider(
                id, id, "", false, false, false,
                "", "", true, "", "", modelId,
                List.of(new ModelConfigDto.Model(modelId, modelId, List.of(), false, 0, 0, 0, null, true, ""))
        );
    }
}
