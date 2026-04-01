package ai.nomoclaw.bot.application.dto;

import java.time.Instant;

public record ConversationSummaryDto(
        String conversationUid,
        String agentGroupUid,
        String agentUid,
        String title,
        Instant createdTime,
        Instant updatedTime
) {
}
