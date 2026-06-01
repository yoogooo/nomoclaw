package ai.nomoclaw.bot.api.dto.agent.request;

public record UpdateAgentTipRequest(
        String title,
        String summary,
        String sourceContent
) {
}
