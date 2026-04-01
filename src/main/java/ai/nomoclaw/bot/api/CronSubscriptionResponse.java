package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record CronSubscriptionResponse(
        String subscriptionUid,
        String jobUid,
        String channel,
        String target,
        boolean enabled,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
