package ai.nomoclaw.bot.api.dto.conversation.response;

import java.util.List;

public record ConversationMessagePageResponse(
        List<ConversationMessageResponse> items,
        boolean hasMore,
        String nextBeforeMessageUid
) {
}
