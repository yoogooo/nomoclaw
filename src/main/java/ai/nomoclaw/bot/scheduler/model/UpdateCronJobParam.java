package ai.nomoclaw.bot.scheduler.model;

public record UpdateCronJobParam(
        String agentUid,
        String title,
        String expression,
        String timezone,
        String endAt,
        String taskContent,
        String status
) {
}
