package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotBlank;

public record OpenFileRequest(
        @NotBlank String path
) {
}
