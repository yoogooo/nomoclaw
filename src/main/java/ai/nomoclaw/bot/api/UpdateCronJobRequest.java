package ai.nomoclaw.bot.api;

public record UpdateCronJobRequest(
        String title,
        String expression,
        String timezone,
        String endAt,
        String taskContent,
        String status
) {
}
