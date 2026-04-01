package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateConversationTitleRequest(@NotBlank String title) {
}
