package ai.nomoclaw.bot.domain;

import java.time.Instant;

public record AgentConversation(
        String conversationUid,
        String agentGroupUid,
        String agentUid,
        String channel,
        String title,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        Instant createdAt,
        Instant updatedAt
) {
}
