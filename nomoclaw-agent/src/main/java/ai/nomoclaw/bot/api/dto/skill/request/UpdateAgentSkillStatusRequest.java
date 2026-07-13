package ai.nomoclaw.bot.api.dto.skill.request;

import jakarta.validation.constraints.NotNull;

public record UpdateAgentSkillStatusRequest(
        @NotNull Boolean enabled
) {
}
