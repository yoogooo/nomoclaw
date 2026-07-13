package ai.nomoclaw.bot.skill.model;

public record ImportSkillFromUrlParam(
        String url,
        boolean attachToAgent
) {
}
