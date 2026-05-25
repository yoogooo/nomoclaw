package ai.nomoclaw.bot.planner;

import ai.nomoclaw.bot.llm.config.LlmProperties;
import ai.nomoclaw.bot.llm.debug.LlmDebugLogger;
import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.prompt.SkillPromptLoader;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Component
@Slf4j
public class TaskPlanner implements Planner {

    private final LlmProperties llmProperties;
    private final RuntimeChatModelResolver runtimeChatModelResolver;
    private final SkillPromptLoader skillPromptLoader;
    private final LlmDebugLogger llmDebugLogger;

    public TaskPlanner(LlmProperties llmProperties,
                       RuntimeChatModelResolver runtimeChatModelResolver,
                       SkillPromptLoader skillPromptLoader,
                       LlmDebugLogger llmDebugLogger) {
        this.llmProperties = llmProperties;
        this.runtimeChatModelResolver = runtimeChatModelResolver;
        this.skillPromptLoader = skillPromptLoader;
        this.llmDebugLogger = llmDebugLogger;
    }

    @Override
    public ChatResponse reason(List<ChatMessage> memory,
                               List<ToolSpecification> toolSpecifications,
                               ToolChoice toolChoice,
                               PromptLoader.PromptContext promptContext) {
        PreparedRequest preparedRequest = buildRequest(memory, toolSpecifications, toolChoice, promptContext);
        ChatRequest request = preparedRequest.request();
        RuntimeChatModelResolver.ResolvedModel resolvedModel = runtimeChatModelResolver.resolve(promptContext);
        String requestId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        llmDebugLogger.logRequest(
                requestId,
                "task_reason",
                resolvedModel.providerId(),
                resolvedModel.modelId(),
                promptContext,
                preparedRequest.systemPrompt(),
                request.messages(),
                toolChoice,
                extractToolNames(toolSpecifications)
        );
        logReasonStart(resolvedModel, memory, toolSpecifications, toolChoice, preparedRequest.systemPrompt());
        try {
            ChatResponse response = resolvedModel.model().chat(request);
            llmDebugLogger.logResponse(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, response, elapsedMillis(startNanos));
            logReasonFinish(response);
            return response;
        } catch (Exception ex) {
            llmDebugLogger.logError(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, ex, elapsedMillis(startNanos));
            throw ex;
        }
    }

    @Override
    public StreamReasonResult reasonStream(List<ChatMessage> memory,
                                           List<ToolSpecification> toolSpecifications,
                                           ToolChoice toolChoice,
                                           PromptLoader.PromptContext promptContext,
                                           Consumer<String> onDelta) {
        PreparedRequest preparedRequest = buildRequest(memory, toolSpecifications, toolChoice, promptContext);
        ChatRequest request = preparedRequest.request();
        RuntimeChatModelResolver.ResolvedModel resolvedModel = runtimeChatModelResolver.resolve(promptContext);
        String requestId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        llmDebugLogger.logRequest(
                requestId,
                "task_reason",
                resolvedModel.providerId(),
                resolvedModel.modelId(),
                promptContext,
                preparedRequest.systemPrompt(),
                request.messages(),
                toolChoice,
                extractToolNames(toolSpecifications)
        );
        logReasonStart(resolvedModel, memory, toolSpecifications, toolChoice, preparedRequest.systemPrompt());
        if (!resolvedModel.supportsStreaming()) {
            log.info("[Reasoning] stream fallback disabled provider={} model={}", resolvedModel.providerId(), resolvedModel.modelId());
            try {
                ChatResponse response = resolvedModel.model().chat(request);
                llmDebugLogger.logResponse(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                        promptContext, response, elapsedMillis(startNanos));
                String text = response.aiMessage() == null ? "" : response.aiMessage().text();
                logReasonFinish(response);
                return new StreamReasonResult(response, text == null ? "" : text, false);
            } catch (Exception ex) {
                llmDebugLogger.logError(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                        promptContext, ex, elapsedMillis(startNanos));
                throw ex;
            }
        }

        StringBuilder buffer = new StringBuilder();
        final int[] deltaCount = {0};
        CompletableFuture<ChatResponse> completion = new CompletableFuture<>();
        resolvedModel.streamingModel().chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) {
                    return;
                }
                buffer.append(partialResponse);
                deltaCount[0]++;
                if (onDelta != null) {
                    onDelta.accept(partialResponse);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                completion.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                completion.completeExceptionally(error);
            }
        });

        ChatResponse response;
        try {
            response = completion.join();
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            llmDebugLogger.logError(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, cause, elapsedMillis(startNanos));
            throw ex;
        }
        log.info("[Reasoning] stream completed provider={} model={} deltas={} chars={}",
                resolvedModel.providerId(), resolvedModel.modelId(), deltaCount[0], buffer.length());
        llmDebugLogger.logResponse(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                promptContext, response, elapsedMillis(startNanos));
        logReasonFinish(response);
        return new StreamReasonResult(response, buffer.toString(), true);
    }

    private PreparedRequest buildRequest(List<ChatMessage> memory,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolChoice toolChoice,
                                         PromptLoader.PromptContext promptContext) {
        String systemPrompt = resolveSystemPrompt(promptContext, toolSpecifications);
        List<ChatMessage> requestMessages = new ArrayList<>(memory.size() + 1);
        requestMessages.add(SystemMessage.from(systemPrompt));
        requestMessages.addAll(memory);
        ChatRequest request = ChatRequest.builder()
                .messages(requestMessages)
                .toolSpecifications(toolSpecifications)
                .toolChoice(toolChoice)
                .build();
        return new PreparedRequest(request, systemPrompt);
    }

    private record PreparedRequest(ChatRequest request, String systemPrompt) {
    }

    private void logReasonStart(RuntimeChatModelResolver.ResolvedModel resolvedModel,
                                List<ChatMessage> memory,
                                List<ToolSpecification> toolSpecifications,
                                ToolChoice toolChoice,
                                String systemPrompt) {
        log.info("[Reasoning] start provider={} model={} messages={} tools={} toolChoice={} systemPrompt={}",
                resolvedModel.providerId(),
                resolvedModel.modelId(),
                memory.size(),
                toolSpecifications == null ? 0 : toolSpecifications.size(),
                toolChoice,
                systemPrompt);
    }

    private void logReasonFinish(ChatResponse response) {
        String text = response.aiMessage() == null ? "" : response.aiMessage().text();
        int toolCalls = response.aiMessage() == null || response.aiMessage().toolExecutionRequests() == null
                ? 0
                : response.aiMessage().toolExecutionRequests().size();
        TokenUsage usage = response.metadata() == null ? null : response.metadata().tokenUsage();
        Integer inputTokens = usage == null ? null : usage.inputTokenCount();
        Integer outputTokens = usage == null ? null : usage.outputTokenCount();
        Integer totalTokens = usage == null ? null : usage.totalTokenCount();
        log.info("[Reasoning] finish model={} toolCalls={} inputTokens={} outputTokens={} totalTokens={} text={}",
                response.metadata() == null ? "" : response.metadata().modelName(),
                toolCalls,
                inputTokens,
                outputTokens,
                totalTokens,
                text);
    }

    @Override
    public SummaryResult summarize(List<ChatMessage> memory,
                                   String stopReason,
                                   int roundsUsed,
                                   int maxRounds,
                                   PromptLoader.PromptContext promptContext,
                                   List<ToolSpecification> toolSpecifications) {
        List<ChatMessage> summaryMessages = new ArrayList<>(memory.size() + 1);
        summaryMessages.addAll(memory);
        summaryMessages.add(UserMessage.from(
                "请基于当前上下文给出最终总结，要求：\n"
                + "1. 直接回答用户当前结果；\n"
                + "2. 若任务未完成，明确说明原因；\n"
                + "3. 若适合继续，给出简短下一步建议；\n"
                + "4. 不要输出 JSON。\n"
                + "停止原因=" + stopReason + "，已执行轮次=" + roundsUsed + "/" + maxRounds + "。"
        ));

        ChatResponse response = reason(summaryMessages, toolSpecifications, ToolChoice.NONE, promptContext);
        String answer = response.aiMessage() == null ? "" : nullToEmpty(response.aiMessage().text());
        if (!answer.isBlank()) {
            return new SummaryResult(answer.trim(), response);
        }
        return new SummaryResult("任务已停止。停止原因=" + stopReason + "，已执行轮次=" + roundsUsed + "/" + maxRounds + "。", response);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String resolveSystemPrompt(PromptLoader.PromptContext promptContext, List<ToolSpecification> toolSpecifications) {
        String configured = llmProperties.getSystemPrompt();
        String fallback = (configured != null && !configured.isBlank())
                ? configured
                : "You are an autonomous agent. Use tools when needed and answer naturally when finished.";
        String builtinToolsPrompt = renderToolsPrompt(toolSpecifications, false);
        String mcpToolsPrompt = renderToolsPrompt(toolSpecifications, true);
        String toolkitPrompt = skillPromptLoader.buildAgentSkillPrompt(promptContext);
        return PromptLoader.buildSystemPrompt(promptContext, builtinToolsPrompt, mcpToolsPrompt, toolkitPrompt, fallback);
    }

    private String renderToolsPrompt(List<ToolSpecification> toolSpecifications, boolean mcpTools) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(mcpTools
                ? "以下为当前 MCP 工具，请按名称直接发起 tool calls：\n"
                : "以下为当前内置工具，请按名称直接发起 tool calls：\n");
        boolean hasRenderedTool = false;
        for (ToolSpecification toolSpecification : toolSpecifications) {
            if (isMcpTool(toolSpecification) != mcpTools) {
                continue;
            }
            hasRenderedTool = true;
            builder.append("- ").append(toolSpecification.name());
            if (toolSpecification.description() != null && !toolSpecification.description().isBlank()) {
                builder.append(": ").append(toolSpecification.description());
            }
            if (toolSpecification.parameters() != null
                && toolSpecification.parameters().properties() != null
                && !toolSpecification.parameters().properties().isEmpty()) {
                builder.append("\n  参数: ");
                boolean first = true;
                for (String property : toolSpecification.parameters().properties().keySet()) {
                    if (!first) {
                        builder.append(", ");
                    }
                    builder.append(property);
                    first = false;
                }
            }
            builder.append('\n');
        }
        if (!hasRenderedTool) {
            return "";
        }
        return builder.toString().trim();
    }

    private boolean isMcpTool(ToolSpecification toolSpecification) {
        return toolSpecification != null
                && toolSpecification.name() != null
                && toolSpecification.name().startsWith("mcp_");
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 1200 ? text : text.substring(0, 1200) + "...";
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private List<String> extractToolNames(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return List.of();
        }
        return toolSpecifications.stream()
                .map(item -> item == null ? "" : item.name())
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }
}
