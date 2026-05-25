package ai.nomoclaw.bot.llm.debug;

import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.util.JsonUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于 JSONL 的 LLM 调试日志实现。
 */
@Component
@Slf4j
public class LlmDebugJsonlLogger implements LlmDebugLogger {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?86[-\\s]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\b\\d{17}[\\dXx]\\b");
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile("(?i)(authorization\\s*[:=]\\s*)([^,\\s\\\"]+)");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key\\s*[:=]\\s*)([^,\\s\\\"]+)");

    private final LlmDebugProperties properties;

    public LlmDebugJsonlLogger(LlmDebugProperties properties) {
        this.properties = properties;
    }

    @Override
    public void logRequest(String requestId,
                           String scene,
                           String provider,
                           String model,
                           PromptLoader.PromptContext promptContext,
                           String systemPrompt,
                           List<ChatMessage> messages,
                           ToolChoice toolChoice,
                           List<String> toolNames) {
        if (!properties.isEnabled()) {
            return;
        }
        Map<String, Object> event = baseEvent("request", requestId, scene, provider, model, promptContext);
        event.put("messages", toMessagePayload(messages));
        event.put("toolChoice", toolChoice == null ? "" : toolChoice.toString());
        event.put("toolNames", toolNames == null ? List.of() : toolNames);
        writeEvent(event);
    }

    @Override
    public void logResponse(String requestId,
                            String scene,
                            String provider,
                            String model,
                            PromptLoader.PromptContext promptContext,
                            ChatResponse response,
                            long latencyMs) {
        if (!properties.isEnabled()) {
            return;
        }
        Map<String, Object> event = baseEvent("response", requestId, scene, provider, model, promptContext);
        String assistantText = response == null || response.aiMessage() == null ? "" : normalizeText(response.aiMessage().text());
        event.put("assistantText", properties.isIncludeResponseText() ? assistantText : "");
        event.put("toolCalls", response == null || response.aiMessage() == null
                ? List.of()
                : toToolCallPayload(response.aiMessage().toolExecutionRequests()));
        event.put("finishReason", response == null || response.metadata() == null || response.metadata().finishReason() == null
                ? ""
                : response.metadata().finishReason().name());
        event.put("tokenUsage", toTokenUsagePayload(response == null || response.metadata() == null ? null : response.metadata().tokenUsage()));
        event.put("latencyMs", latencyMs);
        writeEvent(event);
    }

    @Override
    public void logError(String requestId,
                         String scene,
                         String provider,
                         String model,
                         PromptLoader.PromptContext promptContext,
                         Throwable error,
                         long latencyMs) {
        if (!properties.isEnabled()) {
            return;
        }
        Map<String, Object> event = baseEvent("error", requestId, scene, provider, model, promptContext);
        event.put("latencyMs", latencyMs);
        event.put("error", error == null ? "" : normalizeText(error.toString()));
        writeEvent(event);
    }

    private Map<String, Object> baseEvent(String phase,
                                          String requestId,
                                          String scene,
                                          String provider,
                                          String model,
                                          PromptLoader.PromptContext promptContext) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ts", Instant.now().toString());
        event.put("phase", phase);
        event.put("requestId", normalizeText(requestId));
        event.put("sessionId", normalizeText(promptContext == null ? "" : promptContext.sessionId()));
        event.put("messageUid", normalizeText(promptContext == null ? "" : promptContext.messageUid()));
        event.put("scene", normalizeText(scene));
        event.put("provider", normalizeText(provider));
        event.put("model", normalizeText(model));
        return event;
    }

    private List<Map<String, String>> toMessagePayload(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> payload = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", message.type() == null ? "" : message.type().name().toLowerCase(Locale.ROOT));
            item.put("content", extractMessageText(message));
            payload.add(item);
        }
        return payload;
    }

    private String extractMessageText(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return normalizeText(userMessage.singleText());
        }
        if (message instanceof SystemMessage systemMessage) {
            return normalizeText(systemMessage.text());
        }
        if (message instanceof AiMessage aiMessage) {
            return normalizeText(aiMessage.text());
        }
        if (message instanceof ToolExecutionResultMessage toolResultMessage) {
            return normalizeText(toolResultMessage.text());
        }
        return normalizeText(message.toString());
    }

    private List<Map<String, String>> toToolCallPayload(List<ToolExecutionRequest> toolExecutionRequests) {
        if (toolExecutionRequests == null || toolExecutionRequests.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> payload = new ArrayList<>(toolExecutionRequests.size());
        for (ToolExecutionRequest request : toolExecutionRequests) {
            if (request == null) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", normalizeText(request.id()));
            item.put("name", normalizeText(request.name()));
            item.put("arguments", normalizeText(request.arguments()));
            payload.add(item);
        }
        return payload;
    }

    private Map<String, Object> toTokenUsagePayload(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", tokenUsage.inputTokenCount());
        payload.put("output", tokenUsage.outputTokenCount());
        payload.put("total", tokenUsage.totalTokenCount());
        return payload;
    }

    private void writeEvent(Map<String, Object> event) {
        try {
            Path logFile = Path.of(properties.getFilePath()).toAbsolutePath().normalize();
            ensureParentDirectory(logFile);
            rollFileIfNeeded(logFile);
            String rawJson = JsonUtil.toJson(event);
            String normalized = normalizeText(rawJson);
            Files.writeString(logFile, normalized + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.warn("[LlmDebugJsonlLogger] failed to write debug log err={}", ex.toString());
        }
    }

    private void ensureParentDirectory(Path logFile) throws Exception {
        Path parent = logFile.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private void rollFileIfNeeded(Path logFile) {
        try {
            long maxSizeBytes = properties.getMaxFileSizeBytes();
            if (maxSizeBytes <= 0 || Files.notExists(logFile)) {
                return;
            }
            long currentSize = Files.size(logFile);
            if (currentSize < maxSizeBytes) {
                return;
            }
            Path rolledPath = logFile.resolveSibling(logFile.getFileName().toString() + ".1");
            Files.deleteIfExists(rolledPath);
            Files.move(logFile, rolledPath);
        } catch (Exception ex) {
            log.warn("[LlmDebugJsonlLogger] failed to roll log file path={} err={}", logFile, ex.toString());
        }
    }

    private String normalizeText(String value) {
        String text = value == null ? "" : value;
        if (properties.isRedactEnabled()) {
            text = redact(text);
        }
        int maxChars = Math.max(64, properties.getMaxCharsPerField());
        if (text.length() > maxChars) {
            return text.substring(0, maxChars) + "...<truncated>";
        }
        return text;
    }

    private String redact(String text) {
        String result = EMAIL_PATTERN.matcher(text).replaceAll("***");
        result = PHONE_PATTERN.matcher(result).replaceAll("***");
        result = ID_CARD_PATTERN.matcher(result).replaceAll("***");
        result = AUTHORIZATION_PATTERN.matcher(result).replaceAll("$1***");
        result = API_KEY_PATTERN.matcher(result).replaceAll("$1***");
        return result;
    }
}
