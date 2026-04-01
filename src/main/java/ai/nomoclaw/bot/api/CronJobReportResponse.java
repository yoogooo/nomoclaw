package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record CronJobReportResponse(
        String jobUid,
        String reportPath,
        String content,
        LocalDateTime updatedTime
) {
}
