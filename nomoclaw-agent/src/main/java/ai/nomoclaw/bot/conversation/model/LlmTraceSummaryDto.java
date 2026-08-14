package ai.nomoclaw.bot.conversation.model;

import java.time.Instant;

/**
 * Lightweight trace entry used by the timeline.
 */
public record LlmTraceSummaryDto(
        String traceUid,
        String requestUid,
        String scene,
        int roundIndex,
        int attemptIndex,
        String provider,
        String modelName,
        String status,
        long latencyMs,
        int inputTokens,
        int cachedInputTokens,
        int outputTokens,
        int reasoningTokens,
        int totalTokens,
        boolean usageAvailable,
        Instant requestStartedTime,
        Instant responseFinishedTime,
        String responsePreview,
        String errorMessage
) {
}
