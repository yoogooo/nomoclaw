package ai.nomoclaw.bot.planner;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.llm.config.LlmProperties;
import ai.nomoclaw.bot.llm.debug.LlmDebugLogger;
import ai.nomoclaw.bot.llm.debug.LlmTraceRecorder;
import ai.nomoclaw.bot.model.TokenUsageScene;
import ai.nomoclaw.bot.orchestrator.TokenUsageRecorder;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;

@Component
@Slf4j
public class TaskPlanner implements Planner {

    private final LlmProperties llmProperties;
    private final RuntimeChatModelResolver runtimeChatModelResolver;
    private final SkillPromptLoader skillPromptLoader;
    private final LlmDebugLogger llmDebugLogger;
    private final TokenUsageRecorder tokenUsageRecorder;
    private final LlmTraceRecorder llmTraceRecorder;

    @Autowired
    public TaskPlanner(LlmProperties llmProperties,
                       RuntimeChatModelResolver runtimeChatModelResolver,
                       SkillPromptLoader skillPromptLoader,
                       LlmDebugLogger llmDebugLogger,
                       TokenUsageRecorder tokenUsageRecorder,
                       LlmTraceRecorder llmTraceRecorder) {
        this.llmProperties = llmProperties;
        this.runtimeChatModelResolver = runtimeChatModelResolver;
        this.skillPromptLoader = skillPromptLoader;
        this.llmDebugLogger = llmDebugLogger;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.llmTraceRecorder = llmTraceRecorder;
    }

    public TaskPlanner(LlmProperties llmProperties,
                       RuntimeChatModelResolver runtimeChatModelResolver,
                       SkillPromptLoader skillPromptLoader,
                       LlmDebugLogger llmDebugLogger) {
        this(llmProperties, runtimeChatModelResolver, skillPromptLoader, llmDebugLogger, null, null);
    }

    @Override
    public ChatResponse reason(List<ChatMessage> memory,
                               List<ToolSpecification> toolSpecifications,
                               ToolChoice toolChoice,
                               PromptLoader.PromptContext promptContext) {
        return reason(memory, toolSpecifications, toolChoice, promptContext, TokenUsageScene.CHAT_REASONING, 1);
    }

    private ChatResponse reason(List<ChatMessage> memory,
                                List<ToolSpecification> toolSpecifications,
                                ToolChoice toolChoice,
                                PromptLoader.PromptContext promptContext,
                                TokenUsageScene usageScene,
                                int roundIndex) {
        PreparedRequest preparedRequest = buildRequest(memory, toolSpecifications, toolChoice, promptContext);
        ChatRequest request = preparedRequest.request();
        RuntimeChatModelResolver.ResolvedModel resolvedModel = runtimeChatModelResolver.resolve(promptContext);
        String requestId = UuidUtil.newUuid();
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
        LlmTraceRecorder.TraceHandle trace = startTrace("task_reason", resolvedModel, promptContext, preparedRequest, toolChoice, toolSpecifications, roundIndex, 1);
        logReasonStart(resolvedModel, memory, toolSpecifications, toolChoice, preparedRequest.systemPrompt());
        try {
            ChatResponse response = executeWithRetries(
                    () -> resolvedModel.model().chat(request),
                    resolvedModel,
                    "non-stream"
            );
            llmDebugLogger.logResponse(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, response, elapsedMillis(startNanos));
            llmTraceRecorder.complete(trace, response);
            recordUsage(usageScene, resolvedModel, promptContext, response, trace);
            logReasonFinish(response);
            return response;
        } catch (Exception ex) {
            llmDebugLogger.logError(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, ex, elapsedMillis(startNanos));
            llmTraceRecorder.fail(trace, ex);
            throw ex;
        }
    }

    @Override
    public StreamReasonResult reasonStream(List<ChatMessage> memory,
                                           List<ToolSpecification> toolSpecifications,
                                           ToolChoice toolChoice,
                                           PromptLoader.PromptContext promptContext,
                                           Consumer<String> onDelta,
                                           Runnable onRetryReset,
                                           Consumer<RetryNotice> onRetry) {
        return reasonStream(memory, toolSpecifications, toolChoice, promptContext, 1, onDelta, onRetryReset, onRetry);
    }

    @Override
    public StreamReasonResult reasonStream(List<ChatMessage> memory,
                                           List<ToolSpecification> toolSpecifications,
                                           ToolChoice toolChoice,
                                           PromptLoader.PromptContext promptContext,
                                           int roundIndex,
                                           Consumer<String> onDelta,
                                           Runnable onRetryReset,
                                           Consumer<RetryNotice> onRetry) {
        PreparedRequest preparedRequest = buildRequest(memory, toolSpecifications, toolChoice, promptContext);
        ChatRequest request = preparedRequest.request();
        RuntimeChatModelResolver.ResolvedModel resolvedModel = runtimeChatModelResolver.resolve(promptContext);
        String requestId = UuidUtil.newUuid();
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
        LlmTraceRecorder.TraceHandle trace = startTrace("task_reason", resolvedModel, promptContext, preparedRequest, toolChoice, toolSpecifications, roundIndex, 1);
        logReasonStart(resolvedModel, memory, toolSpecifications, toolChoice, preparedRequest.systemPrompt());
        if (!resolvedModel.supportsStreaming()) {
            log.info("[Reasoning] stream fallback disabled provider={} model={}", resolvedModel.providerId(), resolvedModel.modelId());
            try {
                ChatResponse response = executeWithRetries(
                        () -> resolvedModel.model().chat(request),
                        resolvedModel,
                        "non-stream"
                );
                llmDebugLogger.logResponse(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                        promptContext, response, elapsedMillis(startNanos));
                llmTraceRecorder.complete(trace, response);
                recordUsage(TokenUsageScene.CHAT_REASONING, resolvedModel, promptContext, response, trace);
                String text = response.aiMessage() == null ? "" : response.aiMessage().text();
                logReasonFinish(response);
                return new StreamReasonResult(response, text == null ? "" : text, false);
            } catch (Exception ex) {
                llmDebugLogger.logError(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                        promptContext, ex, elapsedMillis(startNanos));
                llmTraceRecorder.fail(trace, ex);
                throw ex;
            }
        }

        try {
            StreamAttemptResult streamed = executeStreamingWithRecovery(request, resolvedModel, onDelta, onRetryReset, onRetry);
            ChatResponse response = streamed.response();
            if (streamed.streamed()) {
                log.info("[Reasoning] stream completed provider={} model={} deltas={} chars={}",
                        resolvedModel.providerId(), resolvedModel.modelId(), streamed.deltaCount(), streamed.accumulatedText().length());
            } else {
                log.info("[Reasoning] stream fallback to non-stream provider={} model={}",
                        resolvedModel.providerId(), resolvedModel.modelId());
            }
            llmDebugLogger.logResponse(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, response, elapsedMillis(startNanos));
            llmTraceRecorder.complete(trace, response);
            recordUsage(TokenUsageScene.CHAT_REASONING, resolvedModel, promptContext, response, trace);
            logReasonFinish(response);
            return new StreamReasonResult(response, streamed.accumulatedText(), streamed.streamed());
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            llmDebugLogger.logError(requestId, "task_reason", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, cause, elapsedMillis(startNanos));
            llmTraceRecorder.fail(trace, cause);
            throw ex;
        }
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

    private void recordUsage(TokenUsageScene scene,
                             RuntimeChatModelResolver.ResolvedModel resolvedModel,
                             PromptLoader.PromptContext promptContext,
                             ChatResponse response,
                             LlmTraceRecorder.TraceHandle trace) {
        if (tokenUsageRecorder == null) {
            return;
        }
        tokenUsageRecorder.record(scene, resolvedModel.providerId(), resolvedModel.modelId(),
                promptContext == null ? "" : promptContext.sessionId(),
                promptContext == null ? "" : promptContext.messageUid(),
                trace == null ? "" : trace.traceUid(), response);
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

        ChatResponse response = reason(summaryMessages, toolSpecifications, ToolChoice.NONE, promptContext, TokenUsageScene.CHAT_SUMMARY, Math.max(1, roundsUsed));
        String answer = response.aiMessage() == null ? "" : nullToEmpty(response.aiMessage().text());
        if (!answer.isBlank()) {
            return new SummaryResult(answer.trim(), response);
        }
        return new SummaryResult("任务已停止。停止原因=" + stopReason + "，已执行轮次=" + roundsUsed + "/" + maxRounds + "。", response);
    }

    private LlmTraceRecorder.TraceHandle startTrace(String scene,
                                                     RuntimeChatModelResolver.ResolvedModel resolvedModel,
                                                     PromptLoader.PromptContext promptContext,
                                                     PreparedRequest preparedRequest,
                                                     ToolChoice toolChoice,
                                                     List<ToolSpecification> toolSpecifications,
                                                     int roundIndex,
                                                     int attemptIndex) {
        return llmTraceRecorder == null ? null : llmTraceRecorder.start(scene, resolvedModel.providerId(), resolvedModel.modelId(),
                promptContext, preparedRequest.systemPrompt(), preparedRequest.request(), toolChoice, toolSpecifications,
                roundIndex, attemptIndex);
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

    private <T> T executeWithRetries(Supplier<T> action,
                                     RuntimeChatModelResolver.ResolvedModel resolvedModel,
                                     String mode) {
        int maxAttempts = maxRetryAttempts();
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (Exception ex) {
                last = ex;
                Throwable root = rootCause(ex);
                boolean retryable = isRetryableTransportError(root);
                if (!retryable || attempt >= maxAttempts) {
                    throw ex;
                }
                log.warn("[Reasoning] {} retry provider={} model={} attempt={}/{} err={}",
                        mode,
                        resolvedModel.providerId(),
                        resolvedModel.modelId(),
                        attempt,
                        maxAttempts,
                        root.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw last == null ? new IllegalStateException("reasoning failed without exception") : new IllegalStateException(last);
    }

    private StreamAttemptResult executeStreamingWithRecovery(ChatRequest request,
                                                             RuntimeChatModelResolver.ResolvedModel resolvedModel,
                                                             Consumer<String> onDelta,
                                                             Runnable onRetryReset,
                                                             Consumer<RetryNotice> onRetry) {
        int maxAttempts = maxRetryAttempts();
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
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

            try {
                ChatResponse response = completion.join();
                return new StreamAttemptResult(response, buffer.toString(), true, deltaCount[0]);
            } catch (CompletionException ex) {
                Throwable root = rootCause(ex);
                if (!isRetryableTransportError(root)) {
                    throw ex;
                }
                if (buffer.length() > 0) {
                    log.warn("[Reasoning] stream aborted after partial output provider={} model={} deltas={} attempt={}/{} err={}",
                            resolvedModel.providerId(), resolvedModel.modelId(), deltaCount[0], attempt, maxAttempts, root.getMessage());
                    runRetryReset(onRetryReset);
                    last = ex;
                    if (attempt >= maxAttempts) {
                        break;
                    }
                    publishRetryNotice(onRetry, attempt, maxAttempts - 1, root);
                    sleepBeforeRetry(attempt);
                    continue;
                }
                last = ex;
                if (attempt >= maxAttempts) {
                    break;
                }
                log.warn("[Reasoning] stream retry provider={} model={} attempt={}/{} err={}",
                        resolvedModel.providerId(),
                        resolvedModel.modelId(),
                        attempt,
                        maxAttempts,
                        root.getMessage());
                publishRetryNotice(onRetry, attempt, maxAttempts - 1, root);
                sleepBeforeRetry(attempt);
            }
        }

        Throwable root = rootCause(last);
        if (isRetryableTransportError(root)) {
            ChatResponse response = executeWithRetries(
                    () -> resolvedModel.model().chat(request),
                    resolvedModel,
                    "stream-fallback"
            );
            String text = response.aiMessage() == null ? "" : nullToEmpty(response.aiMessage().text());
            return new StreamAttemptResult(response, text, false, 0);
        }
        if (last instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException(last);
    }

    private void runRetryReset(Runnable onRetryReset) {
        if (onRetryReset == null) {
            return;
        }
        onRetryReset.run();
    }

    private void publishRetryNotice(Consumer<RetryNotice> onRetry,
                                    int retryIndex,
                                    int maxRetries,
                                    Throwable throwable) {
        if (onRetry == null) {
            return;
        }
        String reason = throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage().trim();
        onRetry.accept(new RetryNotice(retryIndex, maxRetries, reason));
    }

    private int maxRetryAttempts() {
        Integer configured = llmProperties.getMaxRetries();
        int retries = configured == null ? 0 : Math.max(0, configured);
        return 1 + retries;
    }

    private boolean isRetryableTransportError(Throwable throwable) {
        return throwable instanceof EOFException
                || throwable instanceof SSLException
                || throwable instanceof ConnectException
                || throwable instanceof ClosedChannelException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof HttpTimeoutException
                || isRetryableToolCallParsingError(throwable);
    }

    private boolean isRetryableToolCallParsingError(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return false;
        }
        String message = throwable.getMessage().toLowerCase(Locale.ROOT);
        boolean toolCallError = message.contains("tool call") || message.contains("tool_call");
        boolean parsingError = message.contains("parsing")
                || message.contains("parse")
                || message.contains("invalid json")
                || message.contains("invalid character")
                || message.contains("malformed json");
        return toolCallError && parsingError;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getCause() == null || current.getCause() == current) {
                return current;
            }
            current = current.getCause();
        }
        return throwable;
    }

    private void sleepBeforeRetry(int attempt) {
        long delayMillis = Math.min(2_000L, 250L * attempt);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
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

    private record StreamAttemptResult(ChatResponse response, String accumulatedText, boolean streamed, int deltaCount) {
    }
}
