package ai.nomoclaw.bot.orchestrator;

import dev.langchain4j.model.output.TokenUsage;

import java.lang.reflect.Method;

public final class TokenUsageCacheTokenExtractor {

    private TokenUsageCacheTokenExtractor() {
    }

    public static int extractCachedInputTokens(TokenUsage usage) {
        if (usage == null) {
            return 0;
        }
        Integer value = reflectCachedInputTokens(usage);
        if (value == null || value <= 0) {
            value = reflectCachedTokensFromInputDetails(usage);
        }
        if (value == null || value <= 0) {
            return 0;
        }
        return value;
    }

    private static Integer reflectCachedInputTokens(TokenUsage usage) {
        try {
            Method method = usage.getClass().getMethod("cachedInputTokens");
            Object cached = method.invoke(usage);
            if (cached instanceof Number number) {
                return number.intValue();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer reflectCachedTokensFromInputDetails(TokenUsage usage) {
        try {
            Method inputDetailsMethod = usage.getClass().getMethod("inputTokensDetails");
            Object inputDetails = inputDetailsMethod.invoke(usage);
            if (inputDetails == null) {
                return null;
            }
            Method cachedTokensMethod = inputDetails.getClass().getMethod("cachedTokens");
            Object cached = cachedTokensMethod.invoke(inputDetails);
            if (cached instanceof Number number) {
                return number.intValue();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
