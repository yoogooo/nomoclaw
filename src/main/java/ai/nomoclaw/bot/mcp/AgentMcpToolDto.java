package ai.nomoclaw.bot.mcp;

import java.time.LocalDateTime;

public record AgentMcpToolDto(
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
