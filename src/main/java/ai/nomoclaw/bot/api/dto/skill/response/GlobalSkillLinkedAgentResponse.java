package ai.nomoclaw.bot.api.dto.skill.response;

public record GlobalSkillLinkedAgentResponse(
        String agentUid,
        String agentName,
        String displayName
) {
}
