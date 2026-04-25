package ai.nomoclaw.bot.application.dto;

public record SkillBindingAgentDto(
        String agentUid,
        String agentName,
        String displayName,
        boolean enabled
) {
}
