package ai.nomoclaw.bot.api.dto.cron.response;

import java.time.LocalDateTime;

public record CronSubscriptionResponse(
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
