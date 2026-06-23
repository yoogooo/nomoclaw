package ai.nomoclaw.bot.conversation.model;

import java.util.List;

public record ConversationSearchPageDto(
        List<ConversationSearchResultDto> items,
        boolean hasMore,
        String nextBeforeSortKey
) {
}
