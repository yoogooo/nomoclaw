package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;

public record CronJobReportDto(
        String jobUid,
        String reportPath,
        String content,
        LocalDateTime updatedTime
) {
}
