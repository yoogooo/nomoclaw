package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotNull;

public record UpdateMcpServerStatusRequest(
        @NotNull Boolean enabled
) {
}
