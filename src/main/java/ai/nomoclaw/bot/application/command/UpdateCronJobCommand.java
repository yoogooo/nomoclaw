package ai.nomoclaw.bot.application.command;

public record UpdateCronJobCommand(
        String agentUid,
        String title,
        String expression,
        String timezone,
        String endAt,
        String taskContent,
        String status
) {
}
