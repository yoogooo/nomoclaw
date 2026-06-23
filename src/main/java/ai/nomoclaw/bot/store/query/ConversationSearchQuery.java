package ai.nomoclaw.bot.store.query;

import java.time.Instant;

public record ConversationSearchQuery(
        String agentUid,
        String keywordPattern,
        int limit,
        Instant beforeResultTime,
        Long beforeConversationId
) {
}
