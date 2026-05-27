package ai.nomoclaw.bot.api.dto.conversation.response;

import java.time.Instant;

public record ConversationSummaryResponse(
        String conversationUid,
        String agentGroupUid,
        String agentUid,
        String title,
        boolean pinned,
        boolean unread,
        Instant lastTaskTerminalTime,
        Instant createdTime,
        Instant updatedTime
) {
}
