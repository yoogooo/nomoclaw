package ai.nomoclaw.bot.api.dto.cron.response;

import java.time.LocalDateTime;

public record CronJobReportResponse(
        String jobUid,
        String reportPath,
        String content,
        LocalDateTime updatedTime
) {
}
