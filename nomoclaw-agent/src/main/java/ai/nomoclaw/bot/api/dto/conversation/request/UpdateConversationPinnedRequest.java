package ai.nomoclaw.bot.api.dto.conversation.request;

import jakarta.validation.constraints.NotNull;

public record UpdateConversationPinnedRequest(@NotNull Boolean pinned) {
}
