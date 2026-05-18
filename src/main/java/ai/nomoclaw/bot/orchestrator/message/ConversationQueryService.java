package ai.nomoclaw.bot.orchestrator.message;

import ai.nomoclaw.bot.conversation.model.ConversationAttachmentDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageRunDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryDto;
import ai.nomoclaw.bot.conversation.model.MessageFileLinkDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.AgentEvent;
import ai.nomoclaw.bot.model.AgentEventType;
import ai.nomoclaw.bot.orchestrator.ConversationAttachmentAppService;
import ai.nomoclaw.bot.orchestrator.view.RunViewAssembler;
import ai.nomoclaw.bot.store.AgentStore;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话与消息查询服务。
 */
@Service
public class ConversationQueryService {

    private final AgentStore store;
    private final ConversationAttachmentAppService conversationAttachmentAppService;
    private final RunViewAssembler runViewAssembler;

    public ConversationQueryService(AgentStore store,
                                    ConversationAttachmentAppService conversationAttachmentAppService,
                                    RunViewAssembler runViewAssembler) {
        this.store = store;
        this.conversationAttachmentAppService = conversationAttachmentAppService;
        this.runViewAssembler = runViewAssembler;
    }

    public List<ConversationSummaryDto> listConversations() {
        return store.listConversations().stream()
                .map(conversation -> new ConversationSummaryDto(
                        conversation.conversationUid(),
                        conversation.agentGroupUid(),
                        conversation.agentUid(),
                        conversation.title(),
                        conversation.pinned(),
                        conversation.createdAt(),
                        conversation.updatedAt()
                ))
                .toList();
    }

    public List<ConversationMessageDto> listMessages(String conversationUid) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        List<AgentMessage> messages = store.listMessagesByConversation(conversationUid);
        Map<String, List<ConversationAttachmentDto>> attachmentsByMessage = conversationAttachmentAppService.listByMessageUids(
                messages.stream().map(AgentMessage::messageUid).toList()
        );
        return messages.stream()
                .map(message -> new ConversationMessageDto(
                        message.messageUid(),
                        emptyToNull(message.parentMessageUid()),
                        message.role(),
                        message.content(),
                        message.status(),
                        message.provider(),
                        message.modelName(),
                        message.inputTokens(),
                        message.outputTokens(),
                        message.totalTokens(),
                        message.createdAt(),
                        buildMessageFileLinks(message),
                        attachmentsByMessage.getOrDefault(message.messageUid(), List.of())
                ))
                .toList();
    }

    public List<ConversationMessageRunDto> listMessageRuns(String conversationUid) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        return store.listMessagesByConversation(conversationUid).stream()
                .filter(message -> "user".equals(message.role()))
                .map(message -> runViewAssembler.toMessageRunResponse(
                        message,
                        store.listSteps(message.messageUid()),
                        store.listEventsByMessage(message.messageUid())
                ))
                .filter(Objects::nonNull)
                .toList();
    }

    public AgentConversation getConversation(String conversationUid) {
        return store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
    }

    private List<MessageFileLinkDto> buildMessageFileLinks(AgentMessage message) {
        if (!"assistant".equals(message.role()) || message.parentMessageUid() == null || message.parentMessageUid().isBlank()) {
            return List.of();
        }
        Map<String, MessageFileLinkDto> files = new LinkedHashMap<>();
        for (AgentEvent event : store.listEventsByMessage(message.parentMessageUid())) {
            if (event.eventType() != AgentEventType.STEP_FINISHED || event.payload() == null || !event.payload().path("success").asBoolean(false)) {
                continue;
            }
            String path = event.payload().path("artifacts").path("path").asString("");
            if (path.isBlank()) {
                continue;
            }
            Path filePath = Path.of(path).toAbsolutePath().normalize();
            if (!Files.isRegularFile(filePath)) {
                continue;
            }
            String key = filePath.toString();
            files.putIfAbsent(key, new MessageFileLinkDto(filePath.getFileName().toString(), key));
        }
        return List.copyOf(files.values());
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
