package ai.nomoclaw.bot.application.command;

public record UpdateCronJobCommand(
        String title,
        String expression,
        String timezone,
        String endAt,
        String taskContent,
        String status
) {
}
