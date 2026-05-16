package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentGroupDefinitionEntity;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.prompt.PromptLoader;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * Unified execution context snapshot for one message run.
 */
public record ExecutionScope(AgentConversation conversation,
                             AgentDefinitionEntity executionAgent,
                             AgentGroupDefinitionEntity conversationGroup,
                             AgentWorkspaceConfig workspaceConfig,
                             RuntimeModelSelection runtimeModel,
                             List<ToolSpecification> availableTools,
                             PromptLoader.PromptContext promptContext) {
}
