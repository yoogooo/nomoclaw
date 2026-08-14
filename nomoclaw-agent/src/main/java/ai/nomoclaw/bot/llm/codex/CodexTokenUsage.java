package ai.nomoclaw.bot.llm.codex;

import dev.langchain4j.model.output.TokenUsage;

public class CodexTokenUsage extends TokenUsage {

    private final Integer cachedInputTokens;
    private final Integer reasoningTokens;

    public CodexTokenUsage(Integer inputTokens,
                           Integer outputTokens,
                           Integer totalTokens,
                           Integer cachedInputTokens,
                           Integer reasoningTokens) {
        super(inputTokens, outputTokens, totalTokens);
        this.cachedInputTokens = cachedInputTokens == null ? 0 : Math.max(0, cachedInputTokens);
        this.reasoningTokens = reasoningTokens == null ? 0 : Math.max(0, reasoningTokens);
    }

    public Integer cachedInputTokens() {
        return cachedInputTokens;
    }

    public Integer reasoningTokens() {
        return reasoningTokens;
    }
}
