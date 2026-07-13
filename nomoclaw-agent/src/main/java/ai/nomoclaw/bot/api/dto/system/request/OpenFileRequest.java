package ai.nomoclaw.bot.api.dto.system.request;

import jakarta.validation.constraints.NotBlank;

public record OpenFileRequest(
        @NotBlank String path
) {
}
