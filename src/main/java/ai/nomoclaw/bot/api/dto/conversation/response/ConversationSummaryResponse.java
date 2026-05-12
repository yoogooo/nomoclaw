package ai.nomoclaw.bot.api.dto.conversation.response;

import java.time.Instant;

public record ConversationSummaryResponse(
        String conversationUid,
        String agentGroupUid,
        String agentUid,
        String title,
        boolean pinned,
        Instant createdTime,
        Instant updatedTime
) {
}
