package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record AgentMcpToolResponse(
        String toolKey,
        String serverUid,
        String serverName,
        String serverDisplayName,
        String originalToolName,
        String displayName,
        String description,
        boolean enabled,
        LocalDateTime updatedTime
) {
}
