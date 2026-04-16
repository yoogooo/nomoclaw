package ai.nomoclaw.bot.api;

public record SkillBindingAgentResponse(
        String agentUid,
        String agentName,
        String displayName,
        boolean enabled
) {
}
