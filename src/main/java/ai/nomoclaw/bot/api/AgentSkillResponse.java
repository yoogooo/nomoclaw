package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record AgentSkillResponse(
        String skillKey,
        String displayName,
        String description,
        String skillPath,
        boolean enabled,
        LocalDateTime updatedTime
) {
}
