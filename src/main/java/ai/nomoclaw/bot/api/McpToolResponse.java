package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record McpToolResponse(
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
