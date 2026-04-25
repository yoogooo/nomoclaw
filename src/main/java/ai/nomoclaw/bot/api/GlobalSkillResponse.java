package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;
import java.util.List;

public record GlobalSkillResponse(
        String skillKey,
        String displayName,
        String description,
        String skillPath,
        String status,
        LocalDateTime updatedTime,
        List<GlobalSkillLinkedAgentResponse> linkedAgents
) {
}
