package ai.nomoclaw.bot.api.dto.system.response;

public record TokenUsageBreakdownResponse(String key, long totalTokens, long invocationCount) {
}
