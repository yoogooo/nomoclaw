package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;

public record CronJobDto(
        String jobUid,
        String agentUid,
        String agentName,
        String agentDisplayName,
        String agentAvatar,
        String title,
        boolean registered,
        String triggerState,
        String expression,
        String timezone,
        String endAt,
        String taskContent,
        String status,
        LocalDateTime lastRunTime,
        LocalDateTime nextRunTime,
        String lastResult,
        String lastReportPath,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
