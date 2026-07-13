package ai.nomoclaw.bot.store.query;

import java.time.Instant;

public record ConversationPageQuery(
        String agentUid,
        int limit,
        Instant asOf,
        Integer beforePinned,
        Instant beforeLastUserMessageTime,
        Long beforeId
) {
}
