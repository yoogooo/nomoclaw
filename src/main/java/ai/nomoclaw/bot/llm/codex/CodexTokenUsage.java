package ai.nomoclaw.bot.llm.codex;

import dev.langchain4j.model.output.TokenUsage;

public class CodexTokenUsage extends TokenUsage {

    private final Integer cachedInputTokens;

    public CodexTokenUsage(Integer inputTokens,
                           Integer outputTokens,
                           Integer totalTokens,
                           Integer cachedInputTokens) {
        super(inputTokens, outputTokens, totalTokens);
        this.cachedInputTokens = cachedInputTokens == null ? 0 : Math.max(0, cachedInputTokens);
    }

    public Integer cachedInputTokens() {
        return cachedInputTokens;
    }
}
