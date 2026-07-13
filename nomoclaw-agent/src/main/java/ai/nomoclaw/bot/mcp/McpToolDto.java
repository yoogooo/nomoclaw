package ai.nomoclaw.bot.mcp;

import java.time.LocalDateTime;

public record McpToolDto(
        String toolKey,
        String serverUid,
        String originalToolName,
        String displayName,
        String description,
        String inputSchemaJson,
        String status,
        LocalDateTime lastSyncedTime
) {
}
