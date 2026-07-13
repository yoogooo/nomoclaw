package ai.nomoclaw.bot.scheduler.model;

import java.time.LocalDateTime;

public record CronJobReportDto(
        String jobUid,
        String reportPath,
        String content,
        LocalDateTime updatedTime
) {
}
