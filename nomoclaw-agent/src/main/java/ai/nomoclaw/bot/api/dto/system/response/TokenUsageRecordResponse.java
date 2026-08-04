package ai.nomoclaw.bot.api.dto.system.response;

import java.time.LocalDateTime;

public record TokenUsageRecordResponse(
        String recordUid, String scene, String provider, String modelName,
        String conversationUid, String messageUid, int inputTokens, int cachedInputTokens,
        int outputTokens, int totalTokens, boolean usageAvailable, LocalDateTime occurredTime) {
}
