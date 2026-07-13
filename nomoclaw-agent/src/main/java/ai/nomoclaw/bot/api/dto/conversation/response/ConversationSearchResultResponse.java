package ai.nomoclaw.bot.api.dto.conversation.response;

import java.time.Instant;

public record ConversationSearchResultResponse(
        String conversationUid,
        String agentGroupUid,
        String agentUid,
        String title,
        String previewText,
        Instant resultTime
) {
}
