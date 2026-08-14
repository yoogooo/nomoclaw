package ai.nomoclaw.bot.api.dto.conversation.response;

import java.time.Instant;

/**
 * API response for a complete LLM trace.
 */
public record LlmTraceDetailResponse(
        String traceUid, String requestUid, String conversationUid, String messageUid,
        String scene, int roundIndex, int attemptIndex, String provider, String modelName,
        String status, Instant requestStartedTime, Instant responseFinishedTime, long latencyMs,
        String systemPrompt, String requestMessages, String toolSpecifications, String toolChoice,
        String requestMetadata, String protocolType, String requestUrl, String requestMethod, String requestHeaders,
        String rawRequestJson, Integer responseStatus, String rawResponseJson, String rawStreamEvents,
        String responseContent, String responseThinking, String responseToolCalls, String finishReason,
        int inputTokens, int cachedInputTokens, int outputTokens, int reasoningTokens, int totalTokens,
        boolean usageAvailable, String errorType, String errorMessage
) {
}
