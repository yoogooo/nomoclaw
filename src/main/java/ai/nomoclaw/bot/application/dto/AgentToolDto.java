package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;

public record AgentToolDto(
        String toolKey,
        String displayName,
        String description,
        boolean enabled,
        LocalDateTime updatedTime
) {
}
