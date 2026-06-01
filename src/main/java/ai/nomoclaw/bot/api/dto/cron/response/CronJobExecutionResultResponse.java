package ai.nomoclaw.bot.api.dto.cron.response;

import java.time.LocalDateTime;

public record CronJobExecutionResultResponse(
        String executionUid,
        boolean unread,
        String jobUid,
        String jobTitle,
        String agentUid,
        String agentDisplayName,
        String conversationUid,
        String messageUid,
        LocalDateTime executedTime,
        String status,
        String summary,
        String reportPath,
        String reportContent
) {
}
