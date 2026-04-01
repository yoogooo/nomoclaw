package ai.nomoclaw.bot.store;

import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.AgentEvent;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.StepStatus;

import java.util.List;
import java.util.Optional;

public interface AgentStore {

    AgentConversation createConversation(String conversationUid, String agentGroupUid, String agentUid);

    default AgentConversation createConversation(String conversationUid, String agentGroupUid, String agentUid, String channel) {
        return createConversation(conversationUid, agentGroupUid, agentUid);
    }

    List<AgentConversation> listConversations();

    Optional<AgentConversation> findConversation(String conversationUid);

    void deleteConversation(String conversationUid);

    void touchConversation(String conversationUid);

    void updateConversationTitle(String conversationUid, String title);

    default AgentMessage createUserMessage(String messageUid,
                                           String conversationUid,
                                           String content,
                                           int maxRounds) {
        return createUserMessage(messageUid, conversationUid, content, maxRounds, "", "");
    }

    AgentMessage createUserMessage(String messageUid,
                                   String conversationUid,
                                   String content,
                                   int maxRounds,
                                   String provider,
                                   String modelName);

    AgentMessage createAssistantMessage(String messageUid, String conversationUid, String parentMessageUid, String content);

    Optional<AgentMessage> findMessage(String messageUid);

    List<AgentMessage> listMessagesByConversation(String conversationUid);

    Optional<AgentMessage> findLatestUserMessageByConversation(String conversationUid);

    void updateMessageStatus(String messageUid, MessageStatus status);

    void accumulateMessageTokenUsage(String messageUid,
                                     String provider,
                                     String modelName,
                                     Integer inputTokens,
                                     Integer outputTokens,
                                     Integer totalTokens);

    void saveSteps(String conversationUid, String messageUid, List<PlanStep> steps);

    List<PlanStep> listSteps(String messageUid);

    List<PlanStep> listSteps(String messageUid, int roundIndex);

    Optional<PlanStep> findStep(String stepUid);

    Optional<String> findMessageIdByStep(String stepUid);

    void updateStepStatus(String stepUid, StepStatus status, int retryCount, String errorMessage);

    void updateStepApproval(String stepUid, ApprovalStatus approvalStatus, StepStatus stepStatus);

    void appendEvent(AgentEvent event);

    List<AgentEvent> listEventsByMessage(String messageUid);
}
