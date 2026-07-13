package ai.nomoclaw.bot.api.dto.conversation.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateConversationTitleRequest(@NotBlank String title) {
}
