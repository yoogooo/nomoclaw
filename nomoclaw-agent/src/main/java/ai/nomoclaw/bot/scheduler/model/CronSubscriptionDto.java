package ai.nomoclaw.bot.scheduler.model;

import java.time.LocalDateTime;

public record CronSubscriptionDto(
        String subscriptionUid,
        String jobUid,
        String channel,
        String target,
        String botId,
        boolean enabled,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
