package ai.nomoclaw.bot.api.dto.agent.request;

public record CreateAgentTipRequest(
        String title,
        String summary,
        String sourceContent,
        String sourceConversationUid,
        String sourceMessageUid,
        String sourceTime,
        Boolean generateBestPractice
) {
}
