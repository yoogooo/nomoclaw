package ai.nomoclaw.bot.api.dto.mcp.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateCodexMcpServerRequest(
        @Size(max = 100) String serverName,
        Integer timeoutSeconds,
        Boolean autoStart,
        String command,
        String cwd,
        Map<String, String> env
) {
}
