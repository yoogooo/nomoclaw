package ai.nomoclaw.bot.api.dto.mcp.request;

import jakarta.validation.constraints.NotNull;

public record UpdateMcpServerStatusRequest(
        @NotNull Boolean enabled
) {
}
