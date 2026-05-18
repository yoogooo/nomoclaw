package ai.nomoclaw.bot.agentprofile.model;

public record CreateAgentTipParam(
        String title,
        String summary,
        String sourceContent,
        String sourceConversationUid,
        String sourceMessageUid,
        String sourceTime,
        Boolean generateBestPractice
) {
}
