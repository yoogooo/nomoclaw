package ai.nomoclaw.bot.conversation.model;

import java.time.Instant;

/**
 * Complete trace payload used by the trace detail page.
 */
public record LlmTraceDetailDto(
        String traceUid,
        String requestUid,
        String conversationUid,
        String messageUid,
        String scene,
        int roundIndex,
        int attemptIndex,
        String provider,
        String modelName,
        String status,
        Instant requestStartedTime,
        Instant responseFinishedTime,
        long latencyMs,
        String systemPrompt,
        String requestMessages,
        String toolSpecifications,
        String toolChoice,
        String requestMetadata,
        String responseContent,
        String responseThinking,
        String responseToolCalls,
        String finishReason,
        int inputTokens,
        int cachedInputTokens,
        int outputTokens,
        int totalTokens,
        boolean usageAvailable,
        String errorType,
        String errorMessage
) {
}
