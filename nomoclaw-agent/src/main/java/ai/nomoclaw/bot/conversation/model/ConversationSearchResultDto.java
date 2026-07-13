package ai.nomoclaw.bot.conversation.model;

import java.time.Instant;

public record ConversationSearchResultDto(
        String conversationUid,
        String agentGroupUid,
        String agentUid,
        String title,
        String previewText,
        Instant resultTime
) {
}
