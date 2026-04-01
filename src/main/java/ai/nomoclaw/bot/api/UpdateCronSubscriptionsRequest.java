package ai.nomoclaw.bot.api;

import java.util.List;

public record UpdateCronSubscriptionsRequest(
        List<Item> subscriptions
) {
    public record Item(
            String channel,
            String target,
            boolean enabled
    ) {
    }
}
