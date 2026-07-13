package ai.nomoclaw.bot.api.dto.skill.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateSkillBindingsRequest(
        @NotNull Boolean enabled,
        @Valid List<UpdateSkillBindingAgentRequest> agentBindings
) {
    public record UpdateSkillBindingAgentRequest(
            @NotBlank String agentUid,
            @NotNull Boolean enabled
    ) {
    }
}
