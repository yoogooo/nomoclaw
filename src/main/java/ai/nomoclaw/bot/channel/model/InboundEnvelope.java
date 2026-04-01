package ai.nomoclaw.bot.channel.model;

import java.time.Instant;
import java.util.Map;

public record InboundEnvelope(
        ChannelType channel,
        String tenantId,
        String externalMessageId,
        String sessionKey,
        String senderId,
        String text,
        boolean mentioned,
        String replyTarget,
        Instant receivedAt,
        Map<String, String> metadata
) {

    public InboundEnvelope {
        tenantId = tenantId == null ? "" : tenantId.trim();
        externalMessageId = externalMessageId == null ? "" : externalMessageId.trim();
        sessionKey = sessionKey == null ? "" : sessionKey.trim();
        senderId = senderId == null ? "" : senderId.trim();
        text = text == null ? "" : text;
        replyTarget = replyTarget == null ? "" : replyTarget.trim();
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (sessionKey.isBlank()) {
            throw new IllegalArgumentException("sessionKey must not be blank");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }

    public ChannelSessionKey toSessionKey() {
        return new ChannelSessionKey(channel, tenantId, sessionKey);
    }
}
