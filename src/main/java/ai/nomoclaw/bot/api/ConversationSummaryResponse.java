package ai.nomoclaw.bot.api;

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
