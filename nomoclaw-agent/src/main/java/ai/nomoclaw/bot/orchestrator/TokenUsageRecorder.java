package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.model.TokenUsageScene;
import ai.nomoclaw.bot.llm.codex.CodexTokenUsage;
import ai.nomoclaw.bot.store.entity.TokenUsageRecordEntity;
import ai.nomoclaw.bot.store.repository.TokenUsageRecordRepository;
import ai.nomoclaw.bot.util.UuidUtil;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Persists one immutable usage record for each completed model response.
 */
@Component
public class TokenUsageRecorder {
    private final TokenUsageRecordRepository repository;

    public TokenUsageRecorder(TokenUsageRecordRepository repository) {
        this.repository = repository;
    }

    public void record(TokenUsageScene scene, String provider, String modelName,
                       String conversationUid, String messageUid, ChatResponse response) {
        record(scene, provider, modelName, conversationUid, messageUid, "", response);
    }

    public void record(TokenUsageScene scene, String provider, String modelName,
                       String conversationUid, String messageUid, String traceUid, ChatResponse response) {
        TokenUsage usage = response == null || response.metadata() == null ? null : response.metadata().tokenUsage();
        Integer input = usage == null ? null : usage.inputTokenCount();
        Integer output = usage == null ? null : usage.outputTokenCount();
        Integer total = usage == null ? null : usage.totalTokenCount();
        if (total == null && input != null && output != null) total = input + output;
        TokenUsageRecordEntity entity = new TokenUsageRecordEntity();
        entity.setRecordUid(UuidUtil.newUuid());
        entity.setScene(scene.name());
        entity.setProvider(safe(provider));
        entity.setModelName(safe(modelName));
        entity.setConversationUid(safe(conversationUid));
        entity.setMessageUid(safe(messageUid));
        entity.setTraceUid(safe(traceUid));
        entity.setInputTokens(nonNegative(input));
        entity.setCachedInputTokens(TokenUsageCacheTokenExtractor.extractCachedInputTokens(usage));
        entity.setOutputTokens(nonNegative(output));
        entity.setReasoningTokens(usage instanceof CodexTokenUsage codex ? nonNegative(codex.reasoningTokens()) : 0);
        entity.setTotalTokens(nonNegative(total));
        entity.setUsageAvailable(usage != null);
        entity.setOccurredTime(LocalDateTime.now());
        repository.save(entity);
    }

    private Integer nonNegative(Integer value) { return value == null ? 0 : Math.max(0, value); }
    private String safe(String value) { return value == null ? "" : value.trim(); }
}
