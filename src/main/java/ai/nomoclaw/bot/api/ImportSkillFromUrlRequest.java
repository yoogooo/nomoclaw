package ai.nomoclaw.bot.api;

public record ImportSkillFromUrlRequest(
        String url,
        Boolean attachToAgent
) {
}
