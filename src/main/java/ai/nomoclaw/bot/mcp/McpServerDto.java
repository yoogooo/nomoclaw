package ai.nomoclaw.bot.mcp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record McpServerDto(
        String serverUid,
        String serverName,
        String transport,
        String status,
        Integer timeoutSeconds,
        Boolean autoStart,
        String endpoint,
        Map<String, String> headers,
        String command,
        List<String> args,
        Map<String, String> env,
        String cwd,
        LocalDateTime lastConnectedTime,
        String lastError,
        int toolCount,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
