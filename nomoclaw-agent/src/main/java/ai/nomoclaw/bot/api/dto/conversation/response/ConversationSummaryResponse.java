package ai.nomoclaw.bot.api.dto.conversation.response;

import java.time.Instant;

public record ConversationSummaryResponse(
        String conversationUid,
        String agentGroupUid,
        String agentUid,
        String title,
        boolean pinned,
        boolean running,
        boolean waitingApproval,
        boolean unread,
        Instant lastTaskTerminalTime,
        Instant lastUserMessageTime,
        Instant createdTime,
        Instant updatedTime
) {
}
