package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateAgentRequest(
        @NotBlank String agentName,
        @NotBlank String displayName,
        String description,
        String avatar,
        String avatarColor,
        @NotBlank String modelProvider,
        @NotBlank String modelName,
        List<String> modelNames
) {
}
