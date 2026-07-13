package ai.nomoclaw.bot.api.dto.cron.request;

public record UpdateCronJobRequest(
        String agentUid,
        String title,
        String expression,
        String timezone,
        String endAt,
        String modelProvider,
        String modelName,
        String taskContent,
        String status
) {
}
