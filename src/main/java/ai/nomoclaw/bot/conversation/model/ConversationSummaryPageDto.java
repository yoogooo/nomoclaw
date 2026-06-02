package ai.nomoclaw.bot.conversation.model;

import java.time.Instant;
import java.util.List;

public record ConversationSummaryPageDto(
        List<ConversationSummaryDto> items,
        boolean hasMore,
        String nextBeforeSortKey,
        Instant asOf
) {
}
