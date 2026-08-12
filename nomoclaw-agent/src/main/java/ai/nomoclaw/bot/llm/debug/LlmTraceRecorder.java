package ai.nomoclaw.bot.llm.debug;

import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.store.entity.LlmTraceEntity;
import ai.nomoclaw.bot.store.repository.LlmTraceRepository;
import ai.nomoclaw.bot.orchestrator.TokenUsageCacheTokenExtractor;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.util.UuidUtil;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persists complete, queryable LLM request traces without affecting the chat flow.
 */
@Component
@Slf4j
public class LlmTraceRecorder {

    private final LlmTraceRepository repository;

    public LlmTraceRecorder(LlmTraceRepository repository) {
        this.repository = repository;
    }

    public TraceHandle start(String scene,
                             String provider,
                             String model,
                             PromptLoader.PromptContext context,
                             String systemPrompt,
                             ChatRequest request,
                             ToolChoice toolChoice,
                             List<ToolSpecification> toolSpecifications,
                             int roundIndex,
                             int attemptIndex) {
        TraceHandle handle = new TraceHandle(UuidUtil.newUuid(), UuidUtil.newUuid(), System.nanoTime());
        try {
            LlmTraceEntity entity = new LlmTraceEntity();
            entity.setTraceUid(handle.traceUid());
            entity.setRequestUid(handle.requestUid());
            entity.setConversationUid(value(context == null ? null : context.sessionId()));
            entity.setMessageUid(value(context == null ? null : context.messageUid()));
            entity.setScene(value(scene));
            entity.setRoundIndex(Math.max(1, roundIndex));
            entity.setAttemptIndex(Math.max(1, attemptIndex));
            entity.setProvider(value(provider));
            entity.setModelName(value(model));
            entity.setStatus("REQUESTED");
            entity.setRequestStartedTime(LocalDateTime.now());
            entity.setSystemPrompt(redact(value(systemPrompt)));
            entity.setRequestMessages(redact(JsonUtil.toJson(requestContextPayload(request, toolChoice, toolSpecifications))));
            entity.setToolSpecifications(redact(JsonUtil.toJson(toolSpecificationPayload(toolSpecifications))));
            entity.setToolChoice(redact(value(toolChoice == null ? "" : toolChoice.toString())));
            entity.setRequestMetadata(redact(JsonUtil.toJson(Map.of("requestType", request == null ? "" : request.getClass().getName()))));
            entity.setInputTokens(0);
            entity.setCachedInputTokens(0);
            entity.setOutputTokens(0);
            entity.setTotalTokens(0);
            entity.setUsageAvailable(false);
            repository.save(entity);
        } catch (Exception ex) {
            log.warn("[LlmTrace] failed to persist request trace requestUid={} err={}", handle.requestUid(), ex.toString());
        }
        return handle;
    }

    public void complete(TraceHandle handle, ChatResponse response) {
        if (handle == null) return;
        try {
            LlmTraceEntity entity = repository.findByTraceUid(handle.traceUid());
            if (entity == null) return;
            TokenUsage usage = response == null || response.metadata() == null ? null : response.metadata().tokenUsage();
            entity.setStatus("SUCCEEDED");
            entity.setResponseFinishedTime(LocalDateTime.now());
            entity.setLatencyMs(elapsedMillis(handle.startNanos()));
            entity.setResponseContent(redact(response == null || response.aiMessage() == null ? "" : value(response.aiMessage().text())));
            entity.setResponseThinking(redact(response == null || response.aiMessage() == null ? "" : value(response.aiMessage().thinking())));
            entity.setResponseToolCalls(redact(JsonUtil.toJson(toolCallPayload(
                    response == null || response.aiMessage() == null ? List.of() : response.aiMessage().toolExecutionRequests()))));
            entity.setFinishReason(response == null || response.metadata() == null || response.metadata().finishReason() == null
                    ? "" : response.metadata().finishReason().name());
            entity.setInputTokens(nonNegative(usage == null ? null : usage.inputTokenCount()));
            entity.setCachedInputTokens(TokenUsageCacheTokenExtractor.extractCachedInputTokens(usage));
            entity.setOutputTokens(nonNegative(usage == null ? null : usage.outputTokenCount()));
            Integer total = usage == null ? null : usage.totalTokenCount();
            if (total == null && usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
                total = usage.inputTokenCount() + usage.outputTokenCount();
            }
            entity.setTotalTokens(nonNegative(total));
            entity.setUsageAvailable(usage != null);
            repository.updateById(entity);
        } catch (Exception ex) {
            log.warn("[LlmTrace] failed to persist response traceUid={} err={}", handle.traceUid(), ex.toString());
        }
    }

    public void fail(TraceHandle handle, Throwable error) {
        if (handle == null) return;
        try {
            LlmTraceEntity entity = repository.findByTraceUid(handle.traceUid());
            if (entity == null) return;
            entity.setStatus("FAILED");
            entity.setResponseFinishedTime(LocalDateTime.now());
            entity.setLatencyMs(elapsedMillis(handle.startNanos()));
            entity.setErrorType(error == null ? "" : error.getClass().getName());
            entity.setErrorMessage(redact(error == null ? "" : value(error.getMessage())));
            repository.updateById(entity);
        } catch (Exception ex) {
            log.warn("[LlmTrace] failed to persist error traceUid={} err={}", handle.traceUid(), ex.toString());
        }
    }

    private List<Map<String, String>> messagePayload(List<ChatMessage> messages) {
        List<Map<String, String>> payload = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message == null) continue;
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", message.type() == null ? "" : message.type().name().toLowerCase(Locale.ROOT));
            item.put("content", messageText(message));
            payload.add(item);
        }
        return payload;
    }

    private Map<String, Object> requestContextPayload(ChatRequest request,
                                                      ToolChoice toolChoice,
                                                      List<ToolSpecification> specifications) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", messagePayload(request == null ? List.of() : request.messages()));
        payload.put("tools", toolSpecificationPayload(specifications));
        payload.put("toolChoice", toolChoice == null ? "" : toolChoice.name());
        return payload;
    }

    private List<Map<String, String>> toolCallPayload(List<ToolExecutionRequest> requests) {
        List<Map<String, String>> payload = new ArrayList<>();
        if (requests == null) return payload;
        for (ToolExecutionRequest request : requests) {
            if (request == null) continue;
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", value(request.id()));
            item.put("name", value(request.name()));
            item.put("arguments", value(request.arguments()));
            payload.add(item);
        }
        return payload;
    }

    private List<Object> toolSpecificationPayload(List<ToolSpecification> specifications) {
        List<Object> payload = new ArrayList<>();
        if (specifications == null) return payload;
        for (ToolSpecification specification : specifications) {
            if (specification == null) continue;
            try {
                JsonNode node = JsonUtil.fromJson(specification.toJson(), JsonNode.class);
                payload.add(node);
            } catch (Exception ex) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("name", value(specification.name()));
                fallback.put("description", value(specification.description()));
                fallback.put("parameters", value(specification.parameters() == null ? "" : specification.parameters().toString()));
                fallback.put("strict", specification.strict());
                payload.add(fallback);
            }
        }
        return payload;
    }

    private String messageText(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) return value(systemMessage.text());
        if (message instanceof UserMessage userMessage && userMessage.hasSingleText()) return value(userMessage.singleText());
        if (message instanceof AiMessage aiMessage) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", value(aiMessage.text()));
            payload.put("thinking", value(aiMessage.thinking()));
            payload.put("toolCalls", toolCallPayload(aiMessage.toolExecutionRequests()));
            return JsonUtil.toJson(payload);
        }
        return value(message.toString());
    }

    private String redact(String value) {
        return value.replaceAll("(?i)(authorization\\s*[:=]\\s*|api[_-]?key\\s*[:=]\\s*)([^,\\s\\\"]+)", "$1<redacted>");
    }

    private int nonNegative(Integer value) { return value == null ? 0 : Math.max(0, value); }
    private long elapsedMillis(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000L; }
    private String value(String value) { return value == null ? "" : value; }

    public record TraceHandle(String traceUid, String requestUid, long startNanos) {
    }
}
