package ai.nomoclaw.bot.orchestrator.message;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.orchestrator.ConversationAttachmentAppService;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScopeResolver;
import ai.nomoclaw.bot.orchestrator.execution.MessageExecutionOrchestrator;
import ai.nomoclaw.bot.orchestrator.execution.RuntimeModelSelection;
import ai.nomoclaw.bot.store.AgentStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 消息与会话写入命令服务。
 */
@Service
@Slf4j
public class MessageCommandService {

    public static final String APPROVAL_MODE_DEFAULT = "default";
    private static final String APPROVAL_MODE_FULL_ACCESS = "full_access";
    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";

    private final AgentStore store;
    private final AgentProperties properties;
    private final MessageCancellationRegistry cancellationRegistry;
    private final MessageExecutionOrchestrator messageExecutionOrchestrator;
    private final ExecutionScopeResolver executionScopeResolver;
    private final ConversationAttachmentAppService conversationAttachmentAppService;

    public MessageCommandService(AgentStore store,
                                 AgentProperties properties,
                                 MessageCancellationRegistry cancellationRegistry,
                                 MessageExecutionOrchestrator messageExecutionOrchestrator,
                                 ExecutionScopeResolver executionScopeResolver,
                                 ConversationAttachmentAppService conversationAttachmentAppService) {
        this.store = store;
        this.properties = properties;
        this.cancellationRegistry = cancellationRegistry;
        this.messageExecutionOrchestrator = messageExecutionOrchestrator;
        this.executionScopeResolver = executionScopeResolver;
        this.conversationAttachmentAppService = conversationAttachmentAppService;
    }

    public String createConversation(String agentGroupUid, String agentUid, String channel) {
        String conversationUid = UUID.randomUUID().toString();
        String normalizedGroupUid = normalizeAgentGroupUid(agentGroupUid);
        String normalizedAgentUid = normalizeOptionalAgentUid(agentUid);
        String normalizedChannel = channel == null || channel.isBlank() ? "web" : channel.trim();
        store.createConversation(
                conversationUid,
                normalizedGroupUid,
                normalizedAgentUid,
                normalizedChannel
        );
        log.info("[Agent] conversation created conversationUid={} agentGroupUid={} agentUid={} channel={}",
                conversationUid, normalizedGroupUid, normalizedAgentUid, normalizedChannel);
        return conversationUid;
    }

    public void deleteConversation(String conversationUid) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        List<AgentMessage> messages = store.listMessagesByConversation(conversationUid);
        messages.forEach(message -> {
            cancellationRegistry.cancel(message.messageUid());
            messageExecutionOrchestrator.clearRuntime(message.messageUid());
        });
        conversationAttachmentAppService.purgeConversationAttachments(conversationUid);
        store.deleteConversation(conversationUid);
        log.info("[Agent] conversation deleted conversationUid={} agentGroupUid={} agentUid={}",
                conversationUid, conversation.agentGroupUid(), conversation.agentUid());
    }

    public void updateConversationTitle(String conversationUid, String title) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        store.updateConversationTitle(conversationUid, normalizedTitle);
        log.info("[Agent] conversation title updated conversationUid={} title={} agentGroupUid={} agentUid={}",
                conversationUid, normalizedTitle, conversation.agentGroupUid(), conversation.agentUid());
    }

    public void updateConversationPinned(String conversationUid, boolean pinned) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        store.updateConversationPinned(conversationUid, pinned);
        log.info("[Agent] conversation pin updated conversationUid={} pinned={} agentGroupUid={} agentUid={}",
                conversationUid, pinned, conversation.agentGroupUid(), conversation.agentUid());
    }

    public String submitMessage(String conversationUid,
                                String message,
                                List<String> fileUrls,
                                String modelProvider,
                                String modelName,
                                String approvalMode,
                                String channel,
                                Consumer<String> beforeExecuteHook,
                                MessageExecutionOrchestrator.ExecutionDriver executionDriver) {
        String normalizedChannel = channel == null || channel.isBlank() ? "web" : channel.trim();
        String normalizedApprovalMode = normalizeApprovalMode(approvalMode);
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseGet(() -> store.createConversation(conversationUid, "", DEFAULT_AGENT_UID, normalizedChannel));
        RuntimeModelSelection runtimeModel = executionScopeResolver.resolveForMessageSubmission(conversation, modelProvider, modelName);
        String messageUid = UUID.randomUUID().toString();
        int maxRounds = properties.getLoop().getMaxRounds();
        store.createUserMessage(
                messageUid,
                conversationUid,
                message,
                maxRounds,
                runtimeModel.modelProvider(),
                runtimeModel.modelName()
        );
        conversationAttachmentAppService.attachUploadsToMessage(
                conversationUid,
                messageUid,
                fileUrls,
                runtimeModel.modelProvider(),
                runtimeModel.modelName()
        );
        if (conversation.title() == null || conversation.title().isBlank()) {
            store.updateConversationTitle(conversationUid, buildConversationTitle(message));
        }
        log.info("[Agent] message created conversationUid={} messageUid={} channel={} model={}/{} maxRounds={} message={}",
                conversationUid, messageUid, normalizedChannel, runtimeModel.modelProvider(), runtimeModel.modelName(), maxRounds, summarize(message));
        if (beforeExecuteHook != null) {
            beforeExecuteHook.accept(messageUid);
        }
        messageExecutionOrchestrator.enqueue(messageUid, LocaleContextHolder.getLocale(), normalizedApprovalMode, executionDriver);
        return messageUid;
    }

    public AgentMessage getMessage(String messageUid) {
        return store.findMessage(messageUid)
                .orElseThrow(() -> new IllegalArgumentException("message not found: " + messageUid));
    }

    public int maxLoopRounds() {
        return properties.getLoop().getMaxRounds();
    }

    public String normalizeApprovalMode(String approvalMode) {
        String normalized = approvalMode == null ? "" : approvalMode.trim().toLowerCase();
        return APPROVAL_MODE_FULL_ACCESS.equals(normalized) ? APPROVAL_MODE_FULL_ACCESS : APPROVAL_MODE_DEFAULT;
    }

    private String buildConversationTitle(String message) {
        if (message == null || message.isBlank()) {
            return "未命名对话";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 24) {
            return normalized;
        }
        return normalized.substring(0, 24) + "...";
    }

    private String normalizeAgentGroupUid(String agentGroupUid) {
        return agentGroupUid == null || agentGroupUid.isBlank() ? "" : agentGroupUid.trim();
    }

    private String normalizeOptionalAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? "" : agentUid.trim();
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        int limit = 2000;
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
}
