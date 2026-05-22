package ai.nomoclaw.bot.llm.codex;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CodexUsageCacheRegistry {

    private static final ConcurrentMap<String, Integer> CACHED_INPUT_TOKENS_BY_RESPONSE_ID = new ConcurrentHashMap<>();

    private CodexUsageCacheRegistry() {
    }

    public static void recordCachedInputTokens(String responseId, Integer cachedInputTokens) {
        String normalizedId = trim(responseId);
        if (normalizedId.isBlank()) {
            return;
        }
        int value = cachedInputTokens == null ? 0 : Math.max(0, cachedInputTokens);
        CACHED_INPUT_TOKENS_BY_RESPONSE_ID.put(normalizedId, value);
    }

    public static int takeCachedInputTokens(String responseId) {
        String normalizedId = trim(responseId);
        if (normalizedId.isBlank()) {
            return 0;
        }
        Integer value = CACHED_INPUT_TOKENS_BY_RESPONSE_ID.remove(normalizedId);
        return value == null ? 0 : Math.max(0, value);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
