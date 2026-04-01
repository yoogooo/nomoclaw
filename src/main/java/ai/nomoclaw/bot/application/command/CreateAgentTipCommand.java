package ai.nomoclaw.bot.application.command;

public record CreateAgentTipCommand(
        String title,
        String summary,
        String sourceContent,
        String sourceConversationUid,
        String sourceMessageUid,
        String sourceTime
) {
}
