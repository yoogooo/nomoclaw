package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotNull;

public record UpdateSkillStatusRequest(
        @NotNull Boolean enabled
) {
}
