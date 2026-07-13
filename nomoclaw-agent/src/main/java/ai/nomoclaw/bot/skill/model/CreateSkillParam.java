package ai.nomoclaw.bot.skill.model;

public record CreateSkillParam(
        String skillKey,
        String displayName,
        String description,
        String purpose,
        boolean attachToAgent
) {
}
