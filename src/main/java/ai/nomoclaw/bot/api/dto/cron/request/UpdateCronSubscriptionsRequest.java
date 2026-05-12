package ai.nomoclaw.bot.api.dto.cron.request;

import java.util.List;

public record UpdateCronSubscriptionsRequest(
        List<Item> subscriptions
) {
    public record Item(
            String channel,
            String target,
            String botId,
            boolean enabled
    ) {
    }
}
