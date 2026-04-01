package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;

public record AgentSkillDto(
        String skillKey,
        String displayName,
        String description,
        String skillPath,
        boolean enabled,
        LocalDateTime updatedTime
) {
}
