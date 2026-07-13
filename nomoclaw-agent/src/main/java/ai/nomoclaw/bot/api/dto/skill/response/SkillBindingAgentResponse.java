package ai.nomoclaw.bot.api.dto.skill.response;

public record SkillBindingAgentResponse(
        String agentUid,
        String agentName,
        String displayName,
        boolean enabled
) {
}
