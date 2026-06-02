package ai.nomoclaw.bot.conversation.model;

import java.util.List;

public record ConversationMessagePageDto(
        List<ConversationMessageDto> items,
        boolean hasMore,
        String nextBeforeMessageUid
) {
}
