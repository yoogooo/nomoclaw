package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;

public record CronJobExecutionResultDto(
        LocalDateTime executedTime,
        String status,
        String summary,
        String reportPath,
        String reportContent
) {
}
