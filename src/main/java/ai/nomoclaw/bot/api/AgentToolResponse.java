package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record AgentToolResponse(
        String toolKey,
        String displayName,
        String description,
        boolean enabled,
        LocalDateTime updatedTime
) {
}
