package ai.nomoclaw.bot.api;

public record CreateAgentTipRequest(
        String title,
        String summary,
        String sourceContent,
        String sourceConversationUid,
        String sourceMessageUid,
        String sourceTime
) {
}
