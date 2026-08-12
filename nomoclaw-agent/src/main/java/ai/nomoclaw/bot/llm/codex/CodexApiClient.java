package ai.nomoclaw.bot.llm.codex;

import ai.nomoclaw.bot.util.JsonUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CodexApiClient {

    private static final String DEFAULT_BASE_URL = "https://chatgpt.com/backend-api/codex";
    private static final Logger log = LoggerFactory.getLogger(CodexApiClient.class);

    private final HttpClient httpClient;
    private final CodexTokenProvider tokenProvider;
    private final String baseUrl;
    private final Duration timeout;

    CodexApiClient(HttpClient httpClient,
                   CodexTokenProvider tokenProvider,
                   String baseUrl,
                   Duration timeout) {
        this.httpClient = httpClient;
        this.tokenProvider = tokenProvider;
        this.baseUrl = trim(baseUrl).isBlank() ? DEFAULT_BASE_URL : trim(baseUrl);
        this.timeout = timeout == null ? Duration.ofSeconds(180) : timeout;
    }

    ChatResponse chat(ChatRequest request, String modelName) {
        ChatResponse[] response = new ChatResponse[1];
        Throwable[] error = new Throwable[1];
        stream(request, modelName, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // Synchronous Codex calls still use the streaming endpoint; deltas are collected in stream().
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response[0] = completeResponse;
            }

            @Override
            public void onError(Throwable throwable) {
                error[0] = throwable;
            }
        });
        if (error[0] != null) {
            throw new IllegalStateException("Codex API request failed", error[0]);
        }
        if (response[0] == null) {
            throw new IllegalStateException("Codex API request failed: empty response");
        }
        return response[0];
    }

    void stream(ChatRequest request, String modelName, StreamingChatResponseHandler handler) {
        HttpRequest httpRequest = request(request, modelName, true, "text/event-stream");
        StringBuilder fullText = new StringBuilder();
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        ChatResponse[] completed = new ChatResponse[1];

        try {
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (!isSuccess(response.statusCode())) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw buildApiException(response.statusCode(), body);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                String event = "";
                StringBuilder data = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        handleSseEvent(event, data.toString(), modelName, handler, fullText, toolExecutionRequests, completed);
                        event = "";
                        data.setLength(0);
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        event = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        if (!data.isEmpty()) {
                            data.append('\n');
                        }
                        data.append(line.substring("data:".length()).trim());
                    }
                }
                if (!data.isEmpty()) {
                    handleSseEvent(event, data.toString(), modelName, handler, fullText, toolExecutionRequests, completed);
                }
            }

            if (completed[0] == null) {
                completed[0] = chatResponse("", modelName, fullText.toString(), toolExecutionRequests, null, finishReason(toolExecutionRequests));
            }
            handler.onCompleteResponse(completed[0]);
        } catch (Exception ex) {
            handler.onError(ex);
        }
    }

    private RuntimeException buildApiException(int statusCode, String body) {
        Optional<JsonNode> rootOptional = JsonUtil.fromJsonQuietly(body, JsonNode.class);
        if (statusCode == 429 && rootOptional.isPresent()) {
            JsonNode errorNode = rootOptional.get().path("error");
            String errorType = trim(errorNode.path("type").asText(""));
            if ("usage_limit_reached".equals(errorType)) {
                return new CodexUsageLimitException(
                        statusCode,
                        errorType,
                        trim(errorNode.path("plan_type").asText("")),
                        trim(errorNode.path("message").asText("")),
                        errorNode.path("resets_at").isNumber() ? errorNode.path("resets_at").asLong() : null,
                        errorNode.path("resets_in_seconds").isNumber() ? errorNode.path("resets_in_seconds").asLong() : null
                );
            }
        }
        return new IllegalStateException("Codex streaming API failed: status=" + statusCode + ", body=" + safeBody(body));
    }

    private HttpRequest request(ChatRequest request, String modelName, boolean stream, String accept) {
        String accessToken = trim(tokenProvider.accessToken());
        if (accessToken.isBlank()) {
            throw new IllegalStateException("Codex login token is missing. Please run `codex login` first.");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(baseUrl, "/responses")))
                .timeout(timeout)
                .header("Accept", accept)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("x-codex-installation-id", trim(tokenProvider.installationId()))
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(toCodexRequest(request, modelName, stream))));

        String accountId = trim(tokenProvider.accountId());
        if (!accountId.isBlank()) {
            builder.header("ChatGPT-Account-Id", accountId);
        }
        return builder.build();
    }

    private Map<String, Object> toCodexRequest(ChatRequest request, String modelName, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("instructions", instructions(request.messages()));
        body.put("stream", stream);
        body.put("store", false);
        body.put("input", toInput(request.messages()));
        List<Map<String, Object>> tools = toTools(request.toolSpecifications());
        if (!tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", toToolChoice(request.toolChoice()));
        }
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (request.maxOutputTokens() != null) {
            body.put("max_output_tokens", request.maxOutputTokens());
        }
        return body;
    }

    private List<Map<String, Object>> toTools(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolSpecification toolSpecification : toolSpecifications) {
            if (toolSpecification == null || trim(toolSpecification.name()).isBlank()) {
                continue;
            }
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("name", trim(toolSpecification.name()));
            tool.put("description", trim(toolSpecification.description()));
            if (toolSpecification.parameters() != null) {
                tool.put("parameters", toJsonSchema(toolSpecification.parameters()));
            }
            tools.add(tool);
        }
        return tools;
    }

    private Map<String, Object> toJsonSchema(JsonObjectSchema schema) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "object");
        if (!trim(schema.description()).isBlank()) {
            out.put("description", trim(schema.description()));
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        if (schema.properties() != null) {
            for (Map.Entry<String, JsonSchemaElement> entry : schema.properties().entrySet()) {
                properties.put(entry.getKey(), toJsonSchemaElement(entry.getValue()));
            }
        }
        out.put("properties", properties);
        if (schema.required() != null && !schema.required().isEmpty()) {
            out.put("required", schema.required());
        }
        out.put("additionalProperties", schema.additionalProperties() != null && schema.additionalProperties());
        return out;
    }

    private Map<String, Object> toJsonSchemaElement(JsonSchemaElement element) {
        Map<String, Object> out = new LinkedHashMap<>();
        switch (element) {
            case JsonStringSchema stringSchema -> {
                out.put("type", "string");
                putDescription(out, stringSchema.description());
            }
            case JsonIntegerSchema integerSchema -> {
                out.put("type", "integer");
                putDescription(out, integerSchema.description());
            }
            case JsonNumberSchema numberSchema -> {
                out.put("type", "number");
                putDescription(out, numberSchema.description());
            }
            case JsonBooleanSchema booleanSchema -> {
                out.put("type", "boolean");
                putDescription(out, booleanSchema.description());
            }
            case JsonEnumSchema enumSchema -> {
                out.put("type", "string");
                putDescription(out, enumSchema.description());
                out.put("enum", enumSchema.enumValues() == null ? List.of() : enumSchema.enumValues());
            }
            case JsonArraySchema arraySchema -> {
                out.put("type", "array");
                putDescription(out, arraySchema.description());
                out.put("items", arraySchema.items() == null ? Map.of("type", "string") : toJsonSchemaElement(arraySchema.items()));
            }
            case JsonObjectSchema objectSchema -> out.putAll(toJsonSchema(objectSchema));
            case JsonRawSchema rawSchema ->
                    JsonUtil.fromJsonQuietly(rawSchema.schema(), Map.class).ifPresent(out::putAll);
            case JsonReferenceSchema referenceSchema -> {
                out.put("$ref", referenceSchema.reference());
                putDescription(out, referenceSchema.description());
            }
            case null, default -> out.put("type", "string");
        }
        return out;
    }

    private void putDescription(Map<String, Object> schema, String description) {
        String normalized = trim(description);
        if (!normalized.isBlank()) {
            schema.put("description", normalized);
        }
    }

    private String toToolChoice(ToolChoice toolChoice) {
        if (toolChoice == ToolChoice.NONE) {
            return "none";
        }
        if (toolChoice == ToolChoice.REQUIRED) {
            return "required";
        }
        return "auto";
    }

    private String instructions(List<ChatMessage> messages) {
        List<String> instructions = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                String text = trim(systemMessage.text());
                if (!text.isBlank()) {
                    instructions.add(text);
                }
            }
        }
        return String.join("\n\n", instructions);
    }

    private List<Map<String, Object>> toInput(List<ChatMessage> messages) {
        List<Map<String, Object>> input = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                continue;
            } else if (message instanceof UserMessage userMessage) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", "user");
                item.put("content", toUserContent(userMessage));
                input.add(item);
            } else if (message instanceof AiMessage aiMessage) {
                input.addAll(toAssistantInput(aiMessage));
            } else if (message instanceof ToolExecutionResultMessage toolResultMessage) {
                input.add(toFunctionCallOutput(toolResultMessage));
            } else {
                continue;
            }
        }
        return input;
    }

    private Object toUserContent(UserMessage userMessage) {
        if (userMessage.hasSingleText()) {
            return userMessage.singleText();
        }
        List<Map<String, Object>> content = new ArrayList<>();
        for (Content item : userMessage.contents()) {
            if (item instanceof TextContent textContent) {
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("type", "input_text");
                part.put("text", textContent.text());
                content.add(part);
            } else if (item instanceof ImageContent imageContent) {
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("type", "input_image");
                part.put("image_url", imageUrl(imageContent.image()));
                content.add(part);
            }
        }
        if (content.isEmpty()) {
            return userMessage.toString();
        }
        return content;
    }

    private String imageUrl(Image image) {
        if (image == null) {
            throw new IllegalArgumentException("Codex image input is empty");
        }
        if (image.url() != null) {
            return image.url().toString();
        }
        String base64Data = trim(image.base64Data());
        if (!base64Data.isBlank()) {
            String mimeType = trim(image.mimeType()).isBlank() ? "image/png" : trim(image.mimeType());
            return "data:" + mimeType + ";base64," + base64Data;
        }
        throw new IllegalArgumentException("Codex image input requires URL or base64 data");
    }

    private List<Map<String, Object>> toAssistantInput(AiMessage aiMessage) {
        List<Map<String, Object>> input = new ArrayList<>();
        String text = trim(aiMessage.text());
        if (!text.isBlank()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", "assistant");
            item.put("content", text);
            input.add(item);
        }
        if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                input.add(toFunctionCallInput(request));
            }
        }
        return input;
    }

    private Map<String, Object> toFunctionCallInput(ToolExecutionRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function_call");
        item.put("call_id", trim(request.id()));
        item.put("name", trim(request.name()));
        item.put("arguments", trim(request.arguments()).isBlank() ? "{}" : trim(request.arguments()));
        return item;
    }

    private Map<String, Object> toFunctionCallOutput(ToolExecutionResultMessage message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function_call_output");
        item.put("call_id", trim(message.id()));
        item.put("output", trim(message.text()));
        return item;
    }

    private void handleSseEvent(String event,
                                String data,
                                String modelName,
                                StreamingChatResponseHandler handler,
                                StringBuilder fullText,
                                List<ToolExecutionRequest> toolExecutionRequests,
                                ChatResponse[] completed) {
        String payload = trim(data);
        if (payload.isBlank() || "[DONE]".equals(payload)) {
            return;
        }
        JsonNode root = JsonUtil.fromJson(payload, JsonNode.class);
        String type = firstNonBlank(trim(event), text(root.path("type")));
        if ("response.output_text.delta".equals(type) || "response.refusal.delta".equals(type)) {
            String delta = root.hasNonNull("delta")
                    ? text(root.path("delta"))
                    : text(root.path("text"));
            if (!delta.isEmpty()) {
                fullText.append(delta);
                handler.onPartialResponse(delta);
            }
            return;
        }
        if ("response.output_item.added".equals(type) || "response.output_item.done".equals(type)) {
            JsonNode item = root.path("item").isMissingNode() ? root.path("output_item") : root.path("item");
            ToolExecutionRequest toolExecutionRequest = toToolExecutionRequest(item);
            if (toolExecutionRequest != null) {
                addOrUpdateToolExecutionRequest(toolExecutionRequests, toolExecutionRequest);
            }
            return;
        }
        if ("response.function_call_arguments.done".equals(type)) {
            updateToolExecutionRequestArguments(
                    toolExecutionRequests,
                    firstNonBlank(text(root.path("item_id")), text(root.path("call_id")), text(root.path("id"))),
                    argumentString(root.path("arguments"))
            );
            return;
        }
        if ("response.completed".equals(type)) {
            JsonNode response = root.path("response").isMissingNode() ? root : root.path("response");
            ChatResponse responseWithMaybeTools = toChatResponse(response, modelName, fullText.toString());
            if (!responseWithMaybeTools.aiMessage().hasToolExecutionRequests() && !toolExecutionRequests.isEmpty()) {
                completed[0] = chatResponse(
                        responseWithMaybeTools.id(),
                        modelName,
                        firstNonBlankPreserving(responseWithMaybeTools.aiMessage().text(), fullText.toString()),
                        toolExecutionRequests,
                        responseWithMaybeTools.tokenUsage(),
                        finishReason(toolExecutionRequests)
                );
            } else {
                completed[0] = responseWithMaybeTools;
            }
        }
    }

    private ChatResponse toChatResponse(String body, String modelName) {
        return toChatResponse(JsonUtil.fromJson(body, JsonNode.class), modelName, "");
    }

    private ChatResponse toChatResponse(JsonNode root, String modelName, String fallbackText) {
        String id = text(root.path("id"));
        String outputText = firstNonBlankPreserving(text(root.path("output_text")), extractOutputText(root), fallbackText);
        List<ToolExecutionRequest> toolExecutionRequests = extractToolExecutionRequests(root);
        JsonNode usageNode = root.path("usage");
        if (!usageNode.isMissingNode() && !usageNode.isNull()) {
            log.info("[Reasoning] raw usage responseId={} usage={}", id, usageNode.toString());
        }
        ParsedUsage parsedUsage = parseUsage(usageNode);
        return chatResponse(
                id,
                modelName,
                outputText,
                toolExecutionRequests,
                parsedUsage.tokenUsage(),
                finishReason(toolExecutionRequests)
        );
    }

    private ChatResponse chatResponse(String id,
                                      String modelName,
                                      String outputText,
                                      List<ToolExecutionRequest> toolExecutionRequests,
                                      TokenUsage usage,
                                      FinishReason finishReason) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id(id)
                .modelName(modelName)
                .tokenUsage(usage)
                .finishReason(finishReason)
                .build();
        AiMessage aiMessage = toolExecutionRequests == null || toolExecutionRequests.isEmpty()
                ? AiMessage.from(outputText)
                : AiMessage.from(outputText, toolExecutionRequests);
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(metadata)
                .build();
    }

    private ParsedUsage parseUsage(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return new ParsedUsage(null);
        }
        Integer inputTokens = intOrNull(usage.path("input_tokens"));
        Integer outputTokens = intOrNull(usage.path("output_tokens"));
        Integer totalTokens = intOrNull(usage.path("total_tokens"));
        int cachedInputTokens = maxTokenValue(
                intOrNull(usage.path("input_cached_tokens")),
                intOrNull(usage.path("cache_read_input_tokens")),
                intOrNull(usage.path("input_tokens_details").path("cached_tokens")),
                intOrNull(usage.path("prompt_tokens_details").path("cached_tokens"))
        );
        TokenUsage tokenUsage = new CodexTokenUsage(inputTokens, outputTokens, totalTokens, cachedInputTokens);
        return new ParsedUsage(tokenUsage);
    }

    private int maxTokenValue(Integer... values) {
        int max = 0;
        for (Integer value : values) {
            if (value != null && value > max) {
                max = value;
            }
        }
        return max;
    }

    private record ParsedUsage(TokenUsage tokenUsage) {
    }

    private String extractOutputText(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                String text = firstNonBlankPreserving(text(part.path("text")), text(part.path("content")));
                if (!text.isBlank()) {
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }

    private List<ToolExecutionRequest> extractToolExecutionRequests(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return List.of();
        }
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (JsonNode item : output) {
            ToolExecutionRequest request = toToolExecutionRequest(item);
            if (request != null) {
                requests.add(request);
            }
        }
        return requests;
    }

    private ToolExecutionRequest toToolExecutionRequest(JsonNode item) {
        String type = text(item.path("type"));
        if (!"function_call".equals(type)) {
            return null;
        }
        JsonNode execution = item.path("execution");
        String name = firstNonBlank(
                text(item.path("name")),
                text(item.path("namespace")),
                text(execution.path("name")),
                text(execution.path("namespace"))
        );
        if (name.isBlank()) {
            return null;
        }
        String id = firstNonBlank(text(item.path("call_id")), text(item.path("id")), text(execution.path("call_id")), text(execution.path("id")));
        String arguments = firstNonBlank(
                argumentString(item.path("arguments")),
                argumentString(execution.path("arguments")),
                argumentString(item.path("input")),
                "{}"
        );
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }

    private boolean sameToolCall(ToolExecutionRequest left, ToolExecutionRequest right) {
        if (left == null || right == null) {
            return false;
        }
        String leftId = trim(left.id());
        String rightId = trim(right.id());
        if (!leftId.isBlank() && !rightId.isBlank()) {
            return leftId.equals(rightId);
        }
        return trim(left.name()).equals(trim(right.name())) && trim(left.arguments()).equals(trim(right.arguments()));
    }

    private void addOrUpdateToolExecutionRequest(List<ToolExecutionRequest> requests, ToolExecutionRequest incoming) {
        for (int i = 0; i < requests.size(); i++) {
            ToolExecutionRequest existing = requests.get(i);
            if (sameToolCallIdentity(existing, incoming)) {
                if (shouldReplaceToolCall(existing, incoming)) {
                    requests.set(i, incoming);
                }
                return;
            }
        }
        requests.add(incoming);
    }

    private boolean sameToolCallIdentity(ToolExecutionRequest left, ToolExecutionRequest right) {
        if (left == null || right == null) {
            return false;
        }
        String leftId = trim(left.id());
        String rightId = trim(right.id());
        if (!leftId.isBlank() && !rightId.isBlank()) {
            return leftId.equals(rightId);
        }
        return trim(left.name()).equals(trim(right.name()));
    }

    private boolean shouldReplaceToolCall(ToolExecutionRequest existing, ToolExecutionRequest incoming) {
        if (!hasMeaningfulArguments(existing) && hasMeaningfulArguments(incoming)) {
            return true;
        }
        return trim(incoming.arguments()).length() > trim(existing.arguments()).length();
    }

    private void updateToolExecutionRequestArguments(List<ToolExecutionRequest> requests, String id, String arguments) {
        String normalizedId = trim(id);
        String normalizedArguments = trim(arguments);
        if (normalizedId.isBlank() || normalizedArguments.isBlank()) {
            return;
        }
        for (int i = 0; i < requests.size(); i++) {
            ToolExecutionRequest existing = requests.get(i);
            if (normalizedId.equals(trim(existing.id())) && hasMeaningfulArguments(normalizedArguments)) {
                requests.set(i, ToolExecutionRequest.builder()
                        .id(existing.id())
                        .name(existing.name())
                        .arguments(normalizedArguments)
                        .build());
                return;
            }
        }
    }

    private boolean hasMeaningfulArguments(ToolExecutionRequest request) {
        String arguments = trim(request == null ? "" : request.arguments());
        return hasMeaningfulArguments(arguments);
    }

    private boolean hasMeaningfulArguments(String arguments) {
        arguments = trim(arguments);
        return !arguments.isBlank() && !"{}".equals(arguments) && !"[]".equals(arguments);
    }

    private String argumentString(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isObject() || node.isArray()) {
            return JsonUtil.toJson(node);
        }
        return text(node);
    }

    private FinishReason finishReason(List<ToolExecutionRequest> toolExecutionRequests) {
        return toolExecutionRequests == null || toolExecutionRequests.isEmpty() ? FinishReason.STOP : FinishReason.TOOL_EXECUTION;
    }

    private Integer intOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asString("");
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private String safeBody(String body) {
        String value = trim(body);
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trim(value);
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return "";
    }

    private String firstNonBlankPreserving(String... values) {
        for (String value : values) {
            if (!trim(value).isBlank()) {
                return value == null ? "" : value;
            }
        }
        return "";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
