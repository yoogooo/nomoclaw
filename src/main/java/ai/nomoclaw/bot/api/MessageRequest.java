package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record MessageRequest(
        @NotBlank String message,
        List<String> fileUrls,
        @NotBlank String modelProvider,
        @NotBlank String modelName,
        String approvalMode
) {
}
