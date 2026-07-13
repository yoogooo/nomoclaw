package ai.nomoclaw.bot.orchestrator.execution;

import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;

/**
 * Minimal execution context needed by approval policy decisions.
 */
public record ExecutionApprovalScope(AgentConversation conversation,
                                     AgentMessage message,
                                     AgentDefinitionEntity executionAgent,
                                     AgentWorkspaceConfig workspaceConfig) {
}
