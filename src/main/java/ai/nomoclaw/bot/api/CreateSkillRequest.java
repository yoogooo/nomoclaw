package ai.nomoclaw.bot.api;

public record CreateSkillRequest(
        String skillKey,
        String displayName,
        String description,
        String purpose,
        Boolean attachToAgent
) {
}
