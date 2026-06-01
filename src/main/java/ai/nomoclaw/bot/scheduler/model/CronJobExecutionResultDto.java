package ai.nomoclaw.bot.scheduler.model;

import java.time.LocalDateTime;

public record CronJobExecutionResultDto(
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
