package ai.nomoclaw.bot.tool.model;

import java.time.LocalDateTime;

public record AgentToolDto(
        String toolKey,
        String displayName,
        String description,
        boolean enabled,
        LocalDateTime updatedTime
) {
}
