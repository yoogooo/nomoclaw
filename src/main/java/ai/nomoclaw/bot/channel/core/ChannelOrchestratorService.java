package ai.nomoclaw.bot.channel.core;

import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.repository.ChannelInboundDedupRepository;
import ai.nomoclaw.bot.channel.spi.ChannelMessageRouter;
import ai.nomoclaw.bot.channel.spi.ChannelSessionRepository;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class ChannelOrchestratorService {

    private final AgentApplicationService agentApplicationService;
    private final ChannelSessionRepository channelSessionRepository;
    private final ChannelInboundDedupRepository dedupRepository;
    private final ChannelMessageRouter channelMessageRouter;
    private final AgentChannelsProperties channelProperties;
    private final ChannelPendingReplyContextStore pendingReplyContextStore;

    public ChannelOrchestratorService(AgentApplicationService agentApplicationService,
                                      ChannelSessionRepository channelSessionRepository,
                                      ChannelInboundDedupRepository dedupRepository,
                                      ChannelMessageRouter channelMessageRouter,
                                      AgentChannelsProperties channelProperties,
                                      ChannelPendingReplyContextStore pendingReplyContextStore) {
        this.agentApplicationService = agentApplicationService;
        this.channelSessionRepository = channelSessionRepository;
        this.dedupRepository = dedupRepository;
        this.channelMessageRouter = channelMessageRouter;
        this.channelProperties = channelProperties;
        this.pendingReplyContextStore = pendingReplyContextStore;
    }

    public void processInbound(InboundEnvelope envelope) {
        if (dedupRepository.exists(envelope)) {
            log.info("[Channel] skip duplicate channel={} externalMessageId={}", envelope.channel(), envelope.externalMessageId());
            return;
        }
        ChannelSessionRepository.ChannelSessionRecord session = channelSessionRepository.find(envelope.toSessionKey())
                .orElseGet(() -> {
                    String conversationUid = agentApplicationService.createConversation("", "", envelope.channel().value());
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
        dedupRepository.save(envelope);
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
}
