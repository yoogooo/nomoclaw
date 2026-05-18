package ai.nomoclaw.bot.skill.model;

public record SkillBindingAgentDto(
        String agentUid,
        String agentName,
        String displayName,
        boolean enabled
) {
}
