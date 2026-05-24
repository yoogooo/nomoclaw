package ai.nomoclaw.bot.scheduler.model;

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
        String modelProvider,
        String modelName,
        String taskContent,
        String status,
        String currentExecutionUid,
        String currentConversationUid,
        String currentMessageUid,
        String currentExecutionStatus,
        LocalDateTime currentExecutionStartedTime,
        LocalDateTime lastRunTime,
        LocalDateTime nextRunTime,
        String lastResult,
        String lastReportPath,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
