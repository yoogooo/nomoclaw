package ai.nomoclaw.bot.channel.model;

public record ChannelSessionKey(
        ChannelType channel,
        String tenantId,
        String sessionKey
) {

    public ChannelSessionKey {
        tenantId = tenantId == null ? "" : tenantId.trim();
        sessionKey = sessionKey == null ? "" : sessionKey.trim();
        if (sessionKey.isBlank()) {
            throw new IllegalArgumentException("sessionKey must not be blank");
        }
    }

    public String asUniqueKey() {
        return channel.value() + "|" + tenantId + "|" + sessionKey;
    }
}
