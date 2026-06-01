package ai.nomoclaw.bot.domain;

import ai.nomoclaw.bot.model.MessageStatus;

import java.time.Instant;

public record AgentMessage(
        String messageUid,
        String conversationUid,
        String parentMessageUid,
        String role,
        String content,
        MessageStatus status,
        String provider,
        String modelName,
        int inputTokens,
        int cachedInputTokens,
        int outputTokens,
        int totalTokens,
        Instant createdAt,
        Instant updatedAt
) {
}
