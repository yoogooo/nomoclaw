package ai.nomoclaw.bot.api.dto.conversation.response;

import java.time.Instant;

/**
 * API response for one trace timeline entry.
 */
public record LlmTraceSummaryResponse(
        String traceUid, String requestUid, String scene, int roundIndex, int attemptIndex,
        String provider, String modelName, String status, long latencyMs,
        int inputTokens, int cachedInputTokens, int outputTokens, int totalTokens,
        boolean usageAvailable, Instant requestStartedTime, Instant responseFinishedTime,
        String responsePreview, String errorMessage
) {
}
