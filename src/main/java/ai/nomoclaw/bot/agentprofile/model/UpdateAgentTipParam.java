package ai.nomoclaw.bot.agentprofile.model;

public record UpdateAgentTipParam(
        String title,
        String summary,
        String sourceContent
) {
}
