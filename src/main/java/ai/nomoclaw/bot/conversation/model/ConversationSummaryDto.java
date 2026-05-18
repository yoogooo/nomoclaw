package ai.nomoclaw.bot.conversation.model;

import java.time.Instant;

public record ConversationSummaryDto(
        String conversationUid,
        String agentGroupUid,
        String agentUid,
        String title,
        boolean pinned,
        Instant createdTime,
        Instant updatedTime
) {
}
