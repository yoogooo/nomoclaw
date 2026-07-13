package ai.nomoclaw.bot.channel.core;

import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.channel.config.ChannelBotRouteResolver;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.repository.ChannelInboundDedupRepository;
import ai.nomoclaw.bot.channel.spi.ChannelMessageRouter;
import ai.nomoclaw.bot.channel.spi.ChannelSessionRepository;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScopeResolver;
import ai.nomoclaw.bot.orchestrator.execution.RuntimeModelSelection;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.system.model.ChannelConfigDto;
import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
public class ChannelOrchestratorService {

    private final AgentApplicationService agentApplicationService;
    private final ChannelSessionRepository channelSessionRepository;
    private final ChannelInboundDedupRepository dedupRepository;
    private final ChannelMessageRouter channelMessageRouter;
    private final AgentChannelsProperties channelProperties;
    private final ChannelPendingReplyContextStore pendingReplyContextStore;
    private final ModelConfigAppService modelConfigAppService;
    private final ChannelBotRouteResolver channelBotRouteResolver;
    private final ExecutionScopeResolver executionScopeResolver;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final List<ChannelCommandHandler> commandHandlers;

    public ChannelOrchestratorService(AgentApplicationService agentApplicationService,
                                      ChannelSessionRepository channelSessionRepository,
                                      ChannelInboundDedupRepository dedupRepository,
                                      ChannelMessageRouter channelMessageRouter,
                                      AgentChannelsProperties channelProperties,
                                      ChannelPendingReplyContextStore pendingReplyContextStore,
                                      ModelConfigAppService modelConfigAppService,
                                      ChannelBotRouteResolver channelBotRouteResolver,
                                      ExecutionScopeResolver executionScopeResolver,
                                      AgentDefinitionRepository agentDefinitionRepository) {
        this.agentApplicationService = agentApplicationService;
        this.channelSessionRepository = channelSessionRepository;
        this.dedupRepository = dedupRepository;
        this.channelMessageRouter = channelMessageRouter;
        this.channelProperties = channelProperties;
        this.pendingReplyContextStore = pendingReplyContextStore;
        this.modelConfigAppService = modelConfigAppService;
        this.channelBotRouteResolver = channelBotRouteResolver;
        this.executionScopeResolver = executionScopeResolver;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.commandHandlers = List.of(new NewConversationCommandHandler());
    }

    public void processInbound(InboundEnvelope envelope) {
        if (dedupRepository.exists(envelope)) {
            log.info("[Channel] skip duplicate channel={} externalMessageId={}", envelope.channel(), envelope.externalMessageId());
            return;
        }
        dedupRepository.save(envelope);
        InboundEnvelope normalizedEnvelope = withRouteMetadata(envelope);
        ChannelCommand command = resolveCommand(normalizedEnvelope.text());
        if (command != null) {
            command.execute(normalizedEnvelope);
            return;
        }
        ChannelSessionRepository.ChannelSessionRecord session = channelSessionRepository.find(normalizedEnvelope.toSessionKey())
                .orElseGet(() -> {
                    ChannelBotRouteResolver.BotRouteConfig route = resolveBotRoute(normalizedEnvelope.channel(), normalizedEnvelope.metadata());
                    String conversationUid = agentApplicationService.createConversation("", route.agentUid(), normalizedEnvelope.channel().value());
                    ChannelSessionRepository.ChannelSessionRecord created = new ChannelSessionRepository.ChannelSessionRecord(
                            normalizedEnvelope.toSessionKey(),
                            conversationUid,
                            normalizedEnvelope.replyTarget(),
                            normalizedEnvelope.metadata()
                    );
                    return channelSessionRepository.upsert(created);
                });
        Map<String, String> routeMetadata = mergeMetadata(session.routeMetadata(), normalizedEnvelope.metadata());
        if ((!normalizedEnvelope.replyTarget().isBlank() && !normalizedEnvelope.replyTarget().equals(session.replyTarget()))
                || !routeMetadata.equals(session.routeMetadata())) {
            session = channelSessionRepository.upsert(new ChannelSessionRepository.ChannelSessionRecord(
                    session.key(),
                    session.conversationUid(),
                    normalizedEnvelope.replyTarget().isBlank() ? session.replyTarget() : normalizedEnvelope.replyTarget(),
                    routeMetadata
            ));
        }
        sendProcessingAck(normalizedEnvelope, session);
        String conversationUid = session.conversationUid();
        String replyTarget = session.replyTarget();
        String botId = botIdFromMetadata(session.routeMetadata());
        ChannelBotRouteResolver.BotRouteConfig route = resolveBotRoute(normalizedEnvelope.channel(), session.routeMetadata());
        RuntimeModelChoice requestedModel = resolveRequestedMessageModel(route);
        if (replyTarget == null || replyTarget.isBlank()) {
            if (requestedModel != null) {
                agentApplicationService.submitMessage(
                        conversationUid,
                        normalizedEnvelope.text(),
                        List.of(),
                        requestedModel.modelProvider(),
                        requestedModel.modelName(),
                        "default",
                        normalizedEnvelope.channel().value(),
                        null
                );
                return;
            }
            agentApplicationService.submitMessage(conversationUid, normalizedEnvelope.text(), normalizedEnvelope.channel().value());
            return;
        }
        agentApplicationService.submitMessage(
                conversationUid,
                normalizedEnvelope.text(),
                List.of(),
                requestedModel == null ? "" : requestedModel.modelProvider(),
                requestedModel == null ? "" : requestedModel.modelName(),
                "default",
                normalizedEnvelope.channel().value(),
                messageUid -> pendingReplyContextStore.put(
                        messageUid,
                        normalizedEnvelope.channel(),
                        replyTarget,
                        conversationUid,
                        botId
                )
        );
    }

    private void handleNewConversationCommand(InboundEnvelope envelope, String firstMessage) {
        ChannelSessionRepository.ChannelSessionRecord existingSession = channelSessionRepository.find(envelope.toSessionKey()).orElse(null);
        String replyTarget = envelope.replyTarget().isBlank()
                ? existingSession == null ? "" : existingSession.replyTarget()
                : envelope.replyTarget();
        Map<String, String> routeMetadata = mergeMetadata(
                existingSession == null ? Map.of() : existingSession.routeMetadata(),
                envelope.metadata()
        );
        ChannelBotRouteResolver.BotRouteConfig route = resolveBotRoute(envelope.channel(), routeMetadata);
        String conversationUid = agentApplicationService.createConversation("", route.agentUid(), envelope.channel().value());
        RuntimeModelChoice runtimeModel = resolveNewConversationModel(envelope.channel().value(), route, existingSession);
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
                        conversationUid,
                        botIdFromMetadata(routeMetadata)
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
                    ackMetadata(session, envelope, "processing_ack")
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
                    buildNewConversationAckText(runtimeModel),
                    ackMetadata(session, envelope, "new_conversation")
            );
        } catch (Exception ex) {
            log.warn("[Channel] send new conversation ack failed channel={} sessionKey={}",
                    envelope.channel(), envelope.sessionKey(), ex);
        }
    }

    private RuntimeModelChoice resolveNewConversationModel(String channel,
                                                           ChannelBotRouteResolver.BotRouteConfig route,
                                                           ChannelSessionRepository.ChannelSessionRecord existingSession) {
        RuntimeModelChoice requested = null;
        if (hasExplicitBotModel(route) && isModelConfigured(route.defaultModelProvider(), route.defaultModelName())) {
            requested = new RuntimeModelChoice(route.defaultModelProvider(), route.defaultModelName());
        } else {
            RuntimeModelChoice agentDefault = resolveAgentDefaultModel(route.agentUid());
            if (agentDefault != null && isModelConfigured(agentDefault.modelProvider(), agentDefault.modelName())) {
                requested = agentDefault;
            }
        }
        if (requested == null) {
            RuntimeModelChoice previous = resolvePreviousConversationModel(existingSession);
            if (previous != null) {
                requested = previous;
            }
        }
        AgentConversation conversation = new AgentConversation(
                "",
                "",
                route.agentUid(),
                channel,
                "",
                false,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null
        );
        RuntimeModelSelection resolved = executionScopeResolver.resolveForMessageSubmission(
                conversation,
                requested == null ? "" : requested.modelProvider(),
                requested == null ? "" : requested.modelName()
        );
        return new RuntimeModelChoice(resolved.modelProvider(), resolved.modelName());
    }

    private RuntimeModelChoice resolveAgentDefaultModel(String agentUid) {
        String normalizedAgentUid = trim(agentUid);
        if (normalizedAgentUid.isBlank()) {
            normalizedAgentUid = ChannelConfigDto.DEFAULT_AGENT_UID;
        }
        AgentDefinitionEntity agent = agentDefinitionRepository.findByUid(normalizedAgentUid);
        if (agent == null) {
            return null;
        }
        String provider = trim(agent.getModelProviderId());
        String modelName = resolveAgentPrimaryModelId(agent);
        if (provider.isBlank() || modelName.isBlank()) {
            return null;
        }
        return new RuntimeModelChoice(provider, modelName);
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

    private ChannelCommand resolveCommand(String text) {
        for (ChannelCommandHandler commandHandler : commandHandlers) {
            ChannelCommand command = commandHandler.parse(text);
            if (command != null) {
                return command;
            }
        }
        return null;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private InboundEnvelope withRouteMetadata(InboundEnvelope envelope) {
        Map<String, String> metadata = mergeMetadata(envelope.metadata(), resolveDefaultRouteMetadata(envelope));
        return new InboundEnvelope(
                envelope.channel(),
                envelope.tenantId(),
                envelope.externalMessageId(),
                envelope.sessionKey(),
                envelope.senderId(),
                envelope.text(),
                envelope.mentioned(),
                envelope.replyTarget(),
                envelope.receivedAt(),
                metadata
        );
    }

    private Map<String, String> resolveDefaultRouteMetadata(InboundEnvelope envelope) {
        String botId = botIdFromMetadata(envelope.metadata());
        if (!botId.isBlank()) {
            return Map.of("botId", botId);
        }
        String resolvedBotId = channelBotRouteResolver.resolveDefaultBotId(envelope.channel());
        if (resolvedBotId.isBlank()) {
            return Map.of();
        }
        return Map.of("botId", resolvedBotId);
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

    private Map<String, String> ackMetadata(ChannelSessionRepository.ChannelSessionRecord session,
                                            InboundEnvelope envelope,
                                            String phase) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("conversationUid", session.conversationUid());
        metadata.put("phase", phase);
        metadata.put("externalMessageId", envelope.externalMessageId());
        String botId = botIdFromMetadata(session.routeMetadata());
        if (!botId.isBlank()) {
            metadata.put("botId", botId);
        }
        return Map.copyOf(metadata);
    }

    private ChannelBotRouteResolver.BotRouteConfig resolveBotRoute(ChannelType channel, Map<String, String> metadata) {
        return channelBotRouteResolver.resolve(channel, botIdFromMetadata(metadata));
    }

    private boolean hasExplicitBotModel(ChannelBotRouteResolver.BotRouteConfig route) {
        return !trim(route.defaultModelProvider()).isBlank() && !trim(route.defaultModelName()).isBlank();
    }

    private RuntimeModelChoice resolveRequestedMessageModel(ChannelBotRouteResolver.BotRouteConfig route) {
        if (!hasExplicitBotModel(route) || !isModelConfigured(route.defaultModelProvider(), route.defaultModelName())) {
            return null;
        }
        return new RuntimeModelChoice(route.defaultModelProvider(), route.defaultModelName());
    }

    private String botIdFromMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return "";
        }
        return trim(metadata.get("botId"));
    }

    private String buildNewConversationAckText(RuntimeModelChoice runtimeModel) {
        if (runtimeModel == null || runtimeModel.modelProvider().isBlank() || runtimeModel.modelName().isBlank()) {
            return "已开启新对话，默认模型将按当前 Agent 配置自动选择。";
        }
        return "已开启新对话，默认模型为 " + runtimeModel.modelProvider() + "/" + runtimeModel.modelName() + "。";
    }

    private String resolveAgentPrimaryModelId(AgentDefinitionEntity agent) {
        if (agent == null) {
            return "";
        }
        String extConfig = trim(agent.getExtConfig());
        if (!extConfig.isBlank()) {
            try {
                JsonNode node = JsonUtil.fromJson(extConfig, JsonNode.class);
                JsonNode modelIdsNode = node == null ? null : node.path("modelIds");
                if (modelIdsNode != null && modelIdsNode.isArray()) {
                    for (JsonNode item : modelIdsNode) {
                        String modelId = item == null ? "" : trim(item.asString(""));
                        if (!modelId.isBlank()) {
                            return modelId;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignore malformed extConfig and fall back to the primary model field.
            }
        }
        return trim(agent.getModelId());
    }

    private record RuntimeModelChoice(String modelProvider, String modelName) {
    }

    private interface ChannelCommandHandler {

        ChannelCommand parse(String text);
    }

    private static final class ChannelCommand {

        private final Consumer<InboundEnvelope> executor;

        private ChannelCommand(Consumer<InboundEnvelope> executor) {
            this.executor = executor;
        }

        private void execute(InboundEnvelope envelope) {
            executor.accept(envelope);
        }
    }

    private final class NewConversationCommandHandler implements ChannelCommandHandler {

        private static final String COMMAND = "/new";

        @Override
        public ChannelCommand parse(String text) {
            String normalized = trim(text);
            if (COMMAND.equals(normalized)) {
                return new ChannelCommand(envelope -> handleNewConversationCommand(envelope, ""));
            }
            if (!normalized.startsWith(COMMAND) || normalized.length() <= COMMAND.length()) {
                return null;
            }
            char separator = normalized.charAt(COMMAND.length());
            if (!Character.isWhitespace(separator)) {
                return null;
            }
            String firstMessage = normalized.substring(COMMAND.length() + 1).trim();
            return new ChannelCommand(envelope -> handleNewConversationCommand(envelope, firstMessage));
        }
    }
}
