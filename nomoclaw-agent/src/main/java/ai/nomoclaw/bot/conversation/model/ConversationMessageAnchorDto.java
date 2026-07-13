package ai.nomoclaw.bot.conversation.model;

import java.util.List;

public record ConversationMessageAnchorDto(
        List<ConversationMessageDto> items,
        boolean hasMoreBefore,
        String nextBeforeMessageUid,
        String anchorMessageUid
) {
}
