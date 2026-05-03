package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;

public record CronJobExecutionResultDto(
        String executionUid,
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
