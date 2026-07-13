package ai.nomoclaw.bot.api.dto.conversation.response;

import java.util.List;

public record ConversationMessageAnchorResponse(
        List<ConversationMessageResponse> items,
        boolean hasMoreBefore,
        String nextBeforeMessageUid,
        String anchorMessageUid
) {
}
