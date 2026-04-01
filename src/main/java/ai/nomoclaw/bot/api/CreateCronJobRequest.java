package ai.nomoclaw.bot.api;

public record CreateCronJobRequest(
        String agentUid,
        String title,
        String expression,
        String timezone,
        String endAt,
        String taskContent,
        String status
) {
}
