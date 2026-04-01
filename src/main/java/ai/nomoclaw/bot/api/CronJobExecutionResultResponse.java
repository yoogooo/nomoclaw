package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record CronJobExecutionResultResponse(
        LocalDateTime executedTime,
        String status,
        String summary,
        String reportPath,
        String reportContent
) {
}
