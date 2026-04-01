package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotNull;

public record UpdateAgentToolStatusRequest(
        @NotNull Boolean enabled
) {
}
