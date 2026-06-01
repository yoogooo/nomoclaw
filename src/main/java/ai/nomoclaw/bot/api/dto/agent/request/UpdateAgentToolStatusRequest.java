package ai.nomoclaw.bot.api.dto.agent.request;

import jakarta.validation.constraints.NotNull;

public record UpdateAgentToolStatusRequest(
        @NotNull Boolean enabled
) {
}
