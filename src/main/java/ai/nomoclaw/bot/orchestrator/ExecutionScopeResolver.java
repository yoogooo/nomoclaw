package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.ModelProviderDefaults;
import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentGroupDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentGroupMemberEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupMemberRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Resolves execution scope for message submission, execution and approval.
 */
@Component
public class ExecutionScopeResolver {

    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentGroupDefinitionRepository agentGroupDefinitionRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final ModelConfigAppService modelConfigAppService;
    private final ToolSpecificationRegistry toolSpecificationRegistry;

    public ExecutionScopeResolver(AgentDefinitionRepository agentDefinitionRepository,
                                  AgentGroupDefinitionRepository agentGroupDefinitionRepository,
                                  AgentGroupMemberRepository agentGroupMemberRepository,
                                  ModelConfigAppService modelConfigAppService,
                                  ToolSpecificationRegistry toolSpecificationRegistry) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentGroupDefinitionRepository = agentGroupDefinitionRepository;
        this.agentGroupMemberRepository = agentGroupMemberRepository;
        this.modelConfigAppService = modelConfigAppService;
        this.toolSpecificationRegistry = toolSpecificationRegistry;
    }

    public RuntimeModelSelection resolveForMessageSubmission(AgentConversation conversation,
                                                             String requestedProvider,
                                                             String requestedModel) {
        String providerId = trim(requestedProvider);
        String modelId = trim(requestedModel);
        if (providerId.isBlank() || modelId.isBlank()) {
            RuntimeModelSelection fallback = resolveDefaultRuntimeModel(conversation.agentUid());
            if (providerId.isBlank()) {
                providerId = fallback.modelProvider();
            }
            if (modelId.isBlank()) {
                modelId = fallback.modelName();
            }
        }
        if (providerId.isBlank() || modelId.isBlank()) {
            RuntimeModelSelection fallback = resolveSystemDefaultRuntimeModel();
            if (providerId.isBlank()) {
                providerId = fallback.modelProvider();
            }
            if (modelId.isBlank()) {
                modelId = fallback.modelName();
            }
        }
        validateModelSelection(providerId, List.of(modelId));
        return new RuntimeModelSelection(providerId, modelId);
    }

    public ExecutionScope resolveForExecution(AgentConversation conversation, AgentMessage message) {
        AgentDefinitionEntity executionAgent = resolveExecutionAgent(conversation);
        AgentGroupDefinitionEntity group = resolveConversationGroup(conversation);
        AgentWorkspaceConfig workspaceConfig = resolveExecutionWorkspace(executionAgent);
        List<ToolSpecification> tools = availableToolsForConversation(conversation, executionAgent);
        PromptLoader.PromptContext promptContext = buildPromptContext(
                conversation,
                group,
                executionAgent,
                workspaceConfig,
                conversation.conversationUid(),
                message.messageUid()
        );
        RuntimeModelSelection runtimeModel = new RuntimeModelSelection(trim(message.provider()), trim(message.modelName()));
        return new ExecutionScope(conversation, executionAgent, group, workspaceConfig, runtimeModel, tools, promptContext);
    }

    public ExecutionApprovalScope resolveForApproval(AgentConversation conversation, AgentMessage message) {
        AgentDefinitionEntity executionAgent = resolveExecutionAgent(conversation);
        AgentWorkspaceConfig workspaceConfig = resolveExecutionWorkspace(executionAgent);
        return new ExecutionApprovalScope(conversation, message, executionAgent, workspaceConfig);
    }

    public void validateModelSelection(String modelProvider, List<String> modelIds) {
        String providerId = trim(modelProvider);
        if (providerId.isBlank() || modelIds == null || modelIds.isEmpty()) {
            throw new IllegalArgumentException("modelProvider and modelNames must not be blank");
        }
        ModelConfigDto config = modelConfigAppService.getModelConfig();
        ModelConfigDto.Provider provider = config.providers().stream()
                .filter(item -> providerId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("model provider not found: " + providerId));
        for (String modelId : modelIds) {
            boolean modelExists = provider.models().stream().anyMatch(item -> modelId.equals(item.id()));
            if (!modelExists) {
                throw new IllegalArgumentException("model not found under provider: " + providerId + "/" + modelId);
            }
        }
    }

    private RuntimeModelSelection resolveDefaultRuntimeModel(String agentUid) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        if (normalizedAgentUid.isBlank()) {
            return new RuntimeModelSelection("", "");
        }
        AgentDefinitionEntity agent = agentDefinitionRepository.findByUid(normalizedAgentUid);
        if (agent == null) {
            return new RuntimeModelSelection("", "");
        }
        return new RuntimeModelSelection(trim(agent.getModelProviderId()), resolveAgentPrimaryModelId(agent));
    }

    private RuntimeModelSelection resolveSystemDefaultRuntimeModel() {
        RuntimeModelSelection fromAvailable = pickRuntimeModelFromConfig(modelConfigAppService.getAvailableModelConfig());
        if (!fromAvailable.modelProvider().isBlank() && !fromAvailable.modelName().isBlank()) {
            return fromAvailable;
        }
        RuntimeModelSelection fromConfig = pickRuntimeModelFromConfig(modelConfigAppService.getModelConfig());
        if (!fromConfig.modelProvider().isBlank() && !fromConfig.modelName().isBlank()) {
            return fromConfig;
        }
        return pickRuntimeModelFromProviders(ModelProviderDefaults.providers());
    }

    private RuntimeModelSelection pickRuntimeModelFromConfig(ModelConfigDto config) {
        if (config == null) {
            return new RuntimeModelSelection("", "");
        }
        return pickRuntimeModelFromProviders(config.providers());
    }

    private RuntimeModelSelection pickRuntimeModelFromProviders(List<ModelConfigDto.Provider> providers) {
        if (providers == null || providers.isEmpty()) {
            return new RuntimeModelSelection("", "");
        }
        for (ModelConfigDto.Provider provider : providers) {
            if (provider == null) {
                continue;
            }
            String providerId = trim(provider.id());
            if (providerId.isBlank()) {
                continue;
            }
            String modelId = trim(provider.defaultModel());
            if (modelId.isBlank() && provider.models() != null && !provider.models().isEmpty()) {
                for (ModelConfigDto.Model model : provider.models()) {
                    String candidate = model == null ? "" : trim(model.id());
                    if (!candidate.isBlank()) {
                        modelId = candidate;
                        break;
                    }
                }
            }
            if (!modelId.isBlank()) {
                return new RuntimeModelSelection(providerId, modelId);
            }
        }
        return new RuntimeModelSelection("", "");
    }

    private AgentDefinitionEntity resolveExecutionAgent(AgentConversation conversation) {
        if (conversation.agentUid() != null && !conversation.agentUid().isBlank()) {
            return agentDefinitionRepository.findActiveByUid(conversation.agentUid());
        }
        if (conversation.agentGroupUid() == null || conversation.agentGroupUid().isBlank()) {
            return agentDefinitionRepository.findActiveByUid(DEFAULT_AGENT_UID);
        }
        AgentGroupDefinitionEntity group = agentGroupDefinitionRepository.findActiveByUid(conversation.agentGroupUid());
        if (group != null && group.getOwnerAgentUid() != null && !group.getOwnerAgentUid().isBlank()) {
            AgentDefinitionEntity owner = agentDefinitionRepository.findActiveByUid(group.getOwnerAgentUid());
            if (owner != null) {
                return owner;
            }
        }
        AgentGroupMemberEntity primaryMember = agentGroupMemberRepository.findPrimaryByGroupUid(conversation.agentGroupUid());
        if (primaryMember != null && primaryMember.getAgentUid() != null && !primaryMember.getAgentUid().isBlank()) {
            AgentDefinitionEntity primaryAgent = agentDefinitionRepository.findActiveByUid(primaryMember.getAgentUid());
            if (primaryAgent != null) {
                return primaryAgent;
            }
        }
        return agentDefinitionRepository.findActiveByUid(DEFAULT_AGENT_UID);
    }

    private AgentGroupDefinitionEntity resolveConversationGroup(AgentConversation conversation) {
        if (conversation.agentGroupUid() == null || conversation.agentGroupUid().isBlank()) {
            return null;
        }
        return agentGroupDefinitionRepository.findActiveByUid(conversation.agentGroupUid());
    }

    private AgentWorkspaceConfig resolveExecutionWorkspace(AgentDefinitionEntity agent) {
        if (agent == null) {
            return AgentWorkspaceConfig.defaults(NomoClawPaths.DEFAULT_AGENT_NAME).ensureDirectories();
        }
        return AgentWorkspaceConfig.resolve(agent.getAgentName(), agent.getWorkspace()).ensureDirectories();
    }

    private List<ToolSpecification> availableToolsForConversation(AgentConversation conversation,
                                                                  AgentDefinitionEntity executionAgent) {
        List<ToolSpecification> tools = toolSpecificationRegistry.listForAgent(
                executionAgent == null ? "" : executionAgent.getAgentName()
        );
        String channel = conversation.channel() == null ? "" : conversation.channel().trim().toLowerCase();
        if (!"cron".equals(channel)) {
            return tools;
        }
        return tools.stream().filter(specification -> !isCronTool(specification.name())).toList();
    }

    private PromptLoader.PromptContext buildPromptContext(AgentConversation conversation,
                                                           AgentGroupDefinitionEntity group,
                                                           AgentDefinitionEntity executionAgent,
                                                           AgentWorkspaceConfig workspaceConfig,
                                                           String sessionId,
                                                           String messageUid) {
        String agentName = executionAgent == null ? NomoClawPaths.DEFAULT_AGENT_NAME : executionAgent.getAgentName();
        return PromptLoader.PromptContext.forAgent(
                sessionId,
                messageUid,
                conversation.channel() == null || conversation.channel().isBlank() ? "web" : conversation.channel(),
                group == null ? "" : group.getGroupName(),
                agentName,
                NomoClawPaths.root(),
                NomoClawPaths.agentHome(agentName),
                workspaceConfig.workspaceDir(),
                workspaceConfig.tmpDir(),
                workspaceConfig.reportDir()
        );
    }

    private String resolveAgentPrimaryModelId(AgentDefinitionEntity agent) {
        if (agent == null) {
            return "";
        }
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        String extConfig = trim(agent.getExtConfig());
        if (!extConfig.isBlank()) {
            try {
                JsonNode node = JsonUtil.fromJson(extConfig, JsonNode.class);
                JsonNode modelIdsNode = node == null ? null : node.path("modelIds");
                if (modelIdsNode != null && modelIdsNode.isArray()) {
                    for (JsonNode item : modelIdsNode) {
                        String modelId = item == null ? "" : trim(item.asString(""));
                        if (!modelId.isBlank()) {
                            modelIds.add(modelId);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignore malformed ext_config and fallback to model_id column.
            }
        }
        String fallback = trim(agent.getModelId());
        if (!fallback.isBlank()) {
            modelIds.add(fallback);
        }
        return modelIds.stream().findFirst().orElse("");
    }

    private boolean isCronTool(String toolName) {
        return switch (nullToEmpty(toolName)) {
            case "CronCreateTool", "CronDeleteTool", "CronListTool" -> true;
            default -> false;
        };
    }

    private String normalizeAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? DEFAULT_AGENT_UID : agentUid.trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
