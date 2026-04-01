package ai.nomoclaw.bot.channel.model;

import java.util.Set;

public record ChannelPolicy(
        boolean requireMention,
        Set<String> allowList
) {

    public ChannelPolicy {
        allowList = allowList == null ? Set.of() : Set.copyOf(allowList);
    }

    public boolean allows(String senderId) {
        if (allowList.isEmpty()) {
            return true;
        }
        return allowList.contains(senderId);
    }
}
