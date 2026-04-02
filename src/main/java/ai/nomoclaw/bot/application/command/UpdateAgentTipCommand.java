package ai.nomoclaw.bot.application.command;

public record UpdateAgentTipCommand(
        String title,
        String summary,
        String sourceContent
) {
}
