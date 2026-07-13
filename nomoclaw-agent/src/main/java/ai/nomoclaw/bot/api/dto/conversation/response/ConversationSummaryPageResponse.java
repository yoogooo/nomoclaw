package ai.nomoclaw.bot.api.dto.conversation.response;

import java.time.Instant;
import java.util.List;

public record ConversationSummaryPageResponse(
        List<ConversationSummaryResponse> items,
        boolean hasMore,
        String nextBeforeSortKey,
        Instant asOf
) {
}
