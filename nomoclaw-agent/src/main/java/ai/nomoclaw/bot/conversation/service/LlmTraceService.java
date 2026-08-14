package ai.nomoclaw.bot.conversation.service;

import ai.nomoclaw.bot.conversation.model.LlmTraceDetailDto;
import ai.nomoclaw.bot.conversation.model.LlmTraceSummaryDto;
import ai.nomoclaw.bot.llm.debug.LlmTraceRecord;
import ai.nomoclaw.bot.llm.debug.LlmTraceStore;
import ai.nomoclaw.bot.store.AgentStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Validates ownership and maps persisted LLM traces for the conversation API.
 */
@Service
public class LlmTraceService {

    private final LlmTraceStore traceStore;
    private final AgentStore store;

    public LlmTraceService(LlmTraceStore traceStore, AgentStore store) {
        this.traceStore = traceStore;
        this.store = store;
    }

    public List<LlmTraceSummaryDto> list(String conversationUid, String messageUid) {
        store.findMessage(messageUid)
                .filter(message -> conversationUid.equals(message.conversationUid()))
                .orElseThrow(() -> new IllegalArgumentException("message not found"));
        return traceStore.listByMessageUid(conversationUid, messageUid).stream()
                .map(this::toSummary)
                .toList();
    }

    public LlmTraceDetailDto get(String conversationUid, String traceUid) {
        LlmTraceRecord entity = traceStore.find(conversationUid, traceUid);
        if (entity == null || !conversationUid.equals(entity.getConversationUid())) {
            throw new IllegalArgumentException("trace not found");
        }
        return toDetail(entity);
    }

    private LlmTraceSummaryDto toSummary(LlmTraceRecord item) {
        return new LlmTraceSummaryDto(item.getTraceUid(), item.getRequestUid(), value(item.getScene()),
                value(item.getRoundIndex()), value(item.getAttemptIndex()), value(item.getProvider()), value(item.getModelName()),
                value(item.getStatus()), value(item.getLatencyMs()), value(item.getInputTokens()), value(item.getCachedInputTokens()),
                value(item.getOutputTokens()), value(item.getReasoningTokens()), value(item.getTotalTokens()), Boolean.TRUE.equals(item.getUsageAvailable()),
                instant(item.getRequestStartedTime()), instant(item.getResponseFinishedTime()), preview(item.getResponseContent()), value(item.getErrorMessage()));
    }

    private LlmTraceDetailDto toDetail(LlmTraceRecord item) {
        return new LlmTraceDetailDto(item.getTraceUid(), item.getRequestUid(), item.getConversationUid(), item.getMessageUid(),
                value(item.getScene()), value(item.getRoundIndex()), value(item.getAttemptIndex()), value(item.getProvider()), value(item.getModelName()),
                value(item.getStatus()), instant(item.getRequestStartedTime()), instant(item.getResponseFinishedTime()), value(item.getLatencyMs()),
                value(item.getSystemPrompt()), value(item.getRequestMessages()), value(item.getToolSpecifications()), value(item.getToolChoice()),
                value(item.getRequestMetadata()), value(item.getProtocolType()), value(item.getRequestUrl()), value(item.getRequestMethod()),
                value(item.getRequestHeaders()), value(item.getRawRequestJson()), item.getResponseStatus(), value(item.getRawResponseJson()),
                value(item.getRawStreamEvents()), value(item.getResponseContent()), value(item.getResponseThinking()), value(item.getResponseToolCalls()), value(item.getFinishReason()),
                value(item.getInputTokens()), value(item.getCachedInputTokens()), value(item.getOutputTokens()), value(item.getReasoningTokens()), value(item.getTotalTokens()),
                Boolean.TRUE.equals(item.getUsageAvailable()), value(item.getErrorType()), value(item.getErrorMessage()));
    }

    private String preview(String text) {
        String normalized = value(text).trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "…";
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private long value(Long value) { return value == null ? 0L : value; }
    private String value(String value) { return value == null ? "" : value; }
    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
