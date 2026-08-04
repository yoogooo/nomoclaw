package ai.nomoclaw.bot.api.dto.system.response;

import java.util.List;

public record TokenUsageOverviewResponse(
        long inputTokens, long cachedInputTokens, long outputTokens, long totalTokens,
        long invocationCount, long unavailableUsageCount,
        List<TokenUsageBreakdownResponse> byDay,
        List<TokenUsageBreakdownResponse> byModel,
        List<TokenUsageBreakdownResponse> byScene) {
}
