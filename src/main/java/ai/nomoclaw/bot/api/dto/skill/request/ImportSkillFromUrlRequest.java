package ai.nomoclaw.bot.api.dto.skill.request;

public record ImportSkillFromUrlRequest(
        String url,
        Boolean attachToAgent
) {
}
