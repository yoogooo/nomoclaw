package ai.nomoclaw.bot.api.dto.skill.request;

public record CreateSkillRequest(
        String skillKey,
        String displayName,
        String description,
        String purpose,
        Boolean attachToAgent
) {
}
