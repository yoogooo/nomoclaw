package ai.nomoclaw.bot.channel.model;

import java.time.Instant;
import java.util.Locale;
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

    public boolean isGroupChat() {
        if (channel == ChannelType.FEISHU) {
            String chatType = metadataValue("chatType");
            if (chatType.isBlank()) {
                return false;
            }
            return !"p2p".equals(chatType);
        }
        if (channel == ChannelType.DINGTALK) {
            String conversationType = metadataValue("conversationType");
            return switch (conversationType) {
                case "2", "group", "group_chat", "chat" -> true;
                default -> false;
            };
        }
        if (channel == ChannelType.DISCORD) {
            return !metadataValue("guildId").isBlank();
        }
        if (channel == ChannelType.TELEGRAM) {
            String chatType = metadataValue("chatType");
            return switch (chatType) {
                case "group", "supergroup", "channel" -> true;
                default -> false;
            };
        }
        if (channel == ChannelType.QQ) {
            return !metadataValue("guildId").isBlank() || !metadataValue("groupId").isBlank();
        }
        if (channel == ChannelType.WECOM || channel == ChannelType.WEIXIN) {
            return false;
        }
        return false;
    }

    private String metadataValue(String key) {
        String value = metadata.get(key);
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
