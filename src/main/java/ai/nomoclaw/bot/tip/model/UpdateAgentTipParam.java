package ai.nomoclaw.bot.tip.model;

public record UpdateAgentTipParam(
        String title,
        String summary,
        String sourceContent
) {
}
