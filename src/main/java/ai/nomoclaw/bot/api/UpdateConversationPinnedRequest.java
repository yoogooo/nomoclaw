package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotNull;

public record UpdateConversationPinnedRequest(@NotNull Boolean pinned) {
}
