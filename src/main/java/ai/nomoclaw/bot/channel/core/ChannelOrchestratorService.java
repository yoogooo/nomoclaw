package ai.nomoclaw.bot.channel.core;

import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.channel.repository.ChannelInboundDedupRepository;
import ai.nomoclaw.bot.channel.spi.ChannelMessageRouter;
import ai.nomoclaw.bot.channel.spi.ChannelSessionRepository;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ChannelOrchestratorService {

    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";
    private static final String NEW_CONVERSATION_COMMAND = "/new";
    private static final String IM_DEFAULT_MODEL_PROVIDER = "deepseek";
    private static final String IM_DEFAULT_MODEL_NAME = "deepseek-v4-flash";

    private final AgentApplicationService agentApplicationService;
    private final ChannelSessionRepository channelSessionRepository;
    private final ChannelInboundDedupRepository dedupRepository;
    private final ChannelMessageRouter channelMessageRouter;
    private final AgentChannelsProperties channelProperties;
    private final ChannelPendingReplyContextStore pendingReplyContextStore;
    private final ModelConfigAppService modelConfigAppService;

    public ChannelOrchestratorService(AgentApplicationService agentApplicationService,
                                      ChannelSessionRepository channelSessionRepository,
                                      ChannelInboundDedupRepository dedupRepository,
                                      ChannelMessageRouter channelMessageRouter,
                                      AgentChannelsProperties channelProperties,
                                      ChannelPendingReplyContextStore pendingReplyContextStore,
                                      ModelConfigAppService modelConfigAppService) {
        this.agentApplicationService = agentApplicationService;
        this.channelSessionRepository = channelSessionRepository;
        this.dedupRepository = dedupRepository;
        this.channelMessageRouter = channelMessageRouter;
        this.channelProperties = channelProperties;
        this.pendingReplyContextStore = pendingReplyContextStore;
        this.modelConfigAppService = modelConfigAppService;
    }

    public void processInbound(InboundEnvelope envelope) {
        if (dedupRepository.exists(envelope)) {
            log.info("[Channel] skip duplicate channel={} externalMessageId={}", envelope.channel(), envelope.externalMessageId());
            return;
        }
        dedupRepository.save(envelope);
        String newConversationMessage = extractNewConversationMessage(envelope.text());
        if (newConversationMessage != null) {
            handleNewConversationCommand(envelope, newConversationMessage);
            return;
        }
        ChannelSessionRepository.ChannelSessionRecord session = channelSessionRepository.find(envelope.toSessionKey())
                .orElseGet(() -> {
                    String conversationUid = agentApplicationService.createConversation("", DEFAULT_AGENT_UID, envelope.channel().value());
                    ChannelSessionRepository.ChannelSessionRecord created = new ChannelSessionRepository.ChannelSessionRecord(
                            envelope.toSessionKey(),
                            conversationUid,
                            envelope.replyTarget(),
                            envelope.metadata()
                    );
                    return channelSessionRepository.upsert(created);
                });
        if (!envelope.replyTarget().isBlank() && !envelope.replyTarget().equals(session.replyTarget())) {
            session = channelSessionRepository.upsert(new ChannelSessionRepository.ChannelSessionRecord(
                    session.key(),
                    session.conversationUid(),
                    envelope.replyTarget(),
                    mergeMetadata(session.routeMetadata(), envelope.metadata())
            ));
        }
        sendProcessingAck(envelope, session);
        String conversationUid = session.conversationUid();
        String replyTarget = session.replyTarget();
        if (replyTarget == null || replyTarget.isBlank()) {
            agentApplicationService.submitMessage(conversationUid, envelope.text(), envelope.channel().value());
            return;
        }
        agentApplicationService.submitMessage(
                conversationUid,
                envelope.text(),
                envelope.channel().value(),
                messageUid -> pendingReplyContextStore.put(
                        messageUid,
                        envelope.channel(),
                        replyTarget,
                        conversationUid
                )
        );
    }

    private void handleNewConversationCommand(InboundEnvelope envelope, String firstMessage) {
        ChannelSessionRepository.ChannelSessionRecord existingSession = channelSessionRepository.find(envelope.toSessionKey()).orElse(null);
        String conversationUid = agentApplicationService.createConversation("", DEFAULT_AGENT_UID, envelope.channel().value());
        RuntimeModelChoice runtimeModel = resolveNewConversationModel(existingSession);
        String replyTarget = envelope.replyTarget().isBlank()
                ? existingSession == null ? "" : existingSession.replyTarget()
                : envelope.replyTarget();
        Map<String, String> routeMetadata = mergeMetadata(
                existingSession == null ? Map.of() : existingSession.routeMetadata(),
                envelope.metadata()
        );
        ChannelSessionRepository.ChannelSessionRecord session = channelSessionRepository.upsert(
                new ChannelSessionRepository.ChannelSessionRecord(
                        envelope.toSessionKey(),
                        conversationUid,
                        replyTarget,
                        routeMetadata
                )
        );
        if (firstMessage.isBlank()) {
            sendNewConversationAck(envelope, session, runtimeModel);
            return;
        }
        sendProcessingAck(envelope, session);
        if (replyTarget == null || replyTarget.isBlank()) {
            agentApplicationService.submitMessage(
                    conversationUid,
                    firstMessage,
                    List.of(),
                    runtimeModel.modelProvider(),
                    runtimeModel.modelName(),
                    "default",
                    envelope.channel().value(),
                    null
            );
            return;
        }
        agentApplicationService.submitMessage(
                conversationUid,
                firstMessage,
                List.of(),
                runtimeModel.modelProvider(),
                runtimeModel.modelName(),
                "default",
                envelope.channel().value(),
                messageUid -> pendingReplyContextStore.put(
                        messageUid,
                        envelope.channel(),
                        replyTarget,
                        conversationUid
                )
        );
    }

    private void sendProcessingAck(InboundEnvelope envelope, ChannelSessionRepository.ChannelSessionRecord session) {
        if (!channelProperties.isProcessingAckEnabled()) {
            return;
        }
        if (session.replyTarget() == null || session.replyTarget().isBlank()) {
            return;
        }
        try {
            channelMessageRouter.send(
                    envelope.channel(),
                    session.replyTarget(),
                    channelProperties.getProcessingAckText(),
                    Map.of(
                            "conversationUid", session.conversationUid(),
                            "phase", "processing_ack",
                            "externalMessageId", envelope.externalMessageId()
                    )
            );
        } catch (Exception ex) {
            log.warn("[Channel] send processing ack failed channel={} sessionKey={}",
                    envelope.channel(), envelope.sessionKey(), ex);
        }
    }

    private void sendNewConversationAck(InboundEnvelope envelope,
                                        ChannelSessionRepository.ChannelSessionRecord session,
                                        RuntimeModelChoice runtimeModel) {
        if (session.replyTarget() == null || session.replyTarget().isBlank()) {
            return;
        }
        try {
            channelMessageRouter.send(
                    envelope.channel(),
                    session.replyTarget(),
                    "已开启新对话，默认模型为 " + runtimeModel.modelProvider() + "/" + runtimeModel.modelName() + "。",
                    Map.of(
                            "conversationUid", session.conversationUid(),
                            "phase", "new_conversation",
                            "externalMessageId", envelope.externalMessageId()
                    )
            );
        } catch (Exception ex) {
            log.warn("[Channel] send new conversation ack failed channel={} sessionKey={}",
                    envelope.channel(), envelope.sessionKey(), ex);
        }
    }

    private RuntimeModelChoice resolveNewConversationModel(ChannelSessionRepository.ChannelSessionRecord existingSession) {
        if (isModelConfigured(IM_DEFAULT_MODEL_PROVIDER, IM_DEFAULT_MODEL_NAME)) {
            return new RuntimeModelChoice(IM_DEFAULT_MODEL_PROVIDER, IM_DEFAULT_MODEL_NAME);
        }
        RuntimeModelChoice previous = resolvePreviousConversationModel(existingSession);
        if (previous != null) {
            return previous;
        }
        return new RuntimeModelChoice("", "");
    }

    private RuntimeModelChoice resolvePreviousConversationModel(ChannelSessionRepository.ChannelSessionRecord existingSession) {
        if (existingSession == null || existingSession.conversationUid() == null || existingSession.conversationUid().isBlank()) {
            return null;
        }
        List<ConversationMessageDto> messages = agentApplicationService.listMessages(existingSession.conversationUid());
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConversationMessageDto message = messages.get(i);
            if (message == null || !"user".equals(message.role())) {
                continue;
            }
            String provider = trim(message.provider());
            String modelName = trim(message.modelName());
            if (provider.isBlank() || modelName.isBlank()) {
                continue;
            }
            if (isModelConfigured(provider, modelName)) {
                return new RuntimeModelChoice(provider, modelName);
            }
        }
        return null;
    }

    private boolean isModelConfigured(String providerId, String modelId) {
        String normalizedProvider = trim(providerId);
        String normalizedModel = trim(modelId);
        if (normalizedProvider.isBlank() || normalizedModel.isBlank()) {
            return false;
        }
        ModelConfigDto config = modelConfigAppService.getAvailableModelConfig();
        for (ModelConfigDto.Provider provider : config.providers()) {
            if (provider == null || !normalizedProvider.equals(trim(provider.id()))) {
                continue;
            }
            for (ModelConfigDto.Model model : provider.models()) {
                if (model != null && normalizedModel.equals(trim(model.id()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractNewConversationMessage(String text) {
        String normalized = text == null ? "" : text.trim();
        if (NEW_CONVERSATION_COMMAND.equals(normalized)) {
            return "";
        }
        if (!normalized.startsWith(NEW_CONVERSATION_COMMAND) || normalized.length() <= NEW_CONVERSATION_COMMAND.length()) {
            return null;
        }
        char separator = normalized.charAt(NEW_CONVERSATION_COMMAND.length());
        if (!Character.isWhitespace(separator)) {
            return null;
        }
        return normalized.substring(NEW_CONVERSATION_COMMAND.length() + 1).trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, String> mergeMetadata(Map<String, String> existing, Map<String, String> incoming) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return Map.copyOf(merged);
    }

    private record RuntimeModelChoice(String modelProvider, String modelName) {
    }
}
