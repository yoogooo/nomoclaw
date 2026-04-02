package ai.nomoclaw.bot.api;

public record UpdateAgentTipRequest(
        String title,
        String summary,
        String sourceContent
) {
}
