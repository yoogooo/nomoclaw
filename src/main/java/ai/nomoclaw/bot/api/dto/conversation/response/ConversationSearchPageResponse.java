package ai.nomoclaw.bot.api.dto.conversation.response;

import java.util.List;

public record ConversationSearchPageResponse(
        List<ConversationSearchResultResponse> items,
        boolean hasMore,
        String nextBeforeSortKey
) {
}
