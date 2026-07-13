package ai.nomoclaw.bot.skill.model;

import java.time.LocalDateTime;
import java.util.List;

public record GlobalSkillDto(
        String skillKey,
        String displayName,
        String description,
        String skillPath,
        String status,
        LocalDateTime updatedTime,
        List<SkillLinkedAgentDto> linkedAgents
) {
}
