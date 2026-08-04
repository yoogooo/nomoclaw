package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.llm.debug.LlmDebugLogger;
import ai.nomoclaw.bot.model.TokenUsageScene;
import ai.nomoclaw.bot.planner.RuntimeChatModelResolver;
import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.prompt.PromptTemplateService;
import ai.nomoclaw.bot.util.JsonUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class TipSummaryChatService {

    private static final String TIP_PROMPT_NAMESPACE = "tips";
    private static final String TIP_SYSTEM_TEMPLATE = "system";
    private static final String TIP_USER_INTRO_TEMPLATE = "user-intro";
    private static final List<Locale> SUPPORTED_PROMPT_LOCALES = List.of(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE);
    private static final String DEFAULT_TIP_EVALUATION_SYSTEM_PROMPT = """
            你是“锦囊评估与提炼器”。
            你的唯一任务：判断输入是否值得沉淀为锦囊；如果值得，再提炼成可直接复用的锦囊内容。

            判定标准：
            - 值得保存：包含可复用的方法、步骤、工作流、排错经验、恢复路径、用户纠正后的更优做法，或明显可复用的实践。
            - 不值得保存：只是普通闲聊、一次性结论、过程太少、没有方法论价值、无法复用、信息不足。

            输出要求：
            - 只输出 JSON，不要输出任何额外文字，不要 markdown，不要代码块。
            - 不要编造输入里没有的信息。

            JSON 格式：
            {
              "decision": "save" | "reject",
              "reason": "一句话说明为什么 save/reject",
              "title": "18字以内，decision=save 时填写",
              "summary": "80字以内，decision=save 时填写",
              "content": "300到1000字，decision=save 时填写，必须包含目标、关键步骤顺序、常见失败点、完成判定"
            }
            """;
    private static final String DEFAULT_TIP_EVALUATION_USER_PROMPT_INTRO = """
            请判断下面这段记录是否值得保存为锦囊。

            要求：
            - 如果不值得保存，decision 必须是 reject，reason 必须写明原因，title/summary/content 置空。
            - 如果值得保存，decision 必须是 save，reason 简述值得保存的原因，title/summary/content 按要求提炼。
            - content 必须是可复用、可执行的锦囊，不要原样复述对话。
            """;

    private final RuntimeChatModelResolver runtimeChatModelResolver;
    private final PromptTemplateService promptTemplateService;
    private final LlmDebugLogger llmDebugLogger;
    private final TokenUsageRecorder tokenUsageRecorder;

    protected TipSummaryChatService(RuntimeChatModelResolver runtimeChatModelResolver) {
        this(runtimeChatModelResolver, null, new NoopLlmDebugLogger(), null);
    }

    @Autowired
    public TipSummaryChatService(RuntimeChatModelResolver runtimeChatModelResolver,
                                 PromptTemplateService promptTemplateService,
                                 LlmDebugLogger llmDebugLogger,
                                 TokenUsageRecorder tokenUsageRecorder) {
        this.runtimeChatModelResolver = runtimeChatModelResolver;
        this.promptTemplateService = promptTemplateService;
        this.llmDebugLogger = llmDebugLogger;
        this.tokenUsageRecorder = tokenUsageRecorder;
    }

    public TipSummaryChatService(RuntimeChatModelResolver runtimeChatModelResolver,
                                 PromptTemplateService promptTemplateService,
                                 LlmDebugLogger llmDebugLogger) {
        this(runtimeChatModelResolver, promptTemplateService, llmDebugLogger, null);
    }

    public TipEvaluationResult evaluate(PromptLoader.PromptContext promptContext,
                                        List<RecentMessage> recentMessages,
                                        List<StepDigest> steps,
                                        String finalResult) {
        Locale locale = resolvePromptLocale();
        String systemPrompt = loadSystemPrompt(locale);
        String prompt = buildEvaluationPrompt(recentMessages, steps, finalResult, locale);
        RuntimeChatModelResolver.ResolvedModel resolvedModel = runtimeChatModelResolver.resolve(promptContext);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(prompt)
                ))
                .build();
        String requestId = UuidUtil.newUuid();
        long startNanos = System.nanoTime();
        llmDebugLogger.logRequest(
                requestId,
                "tip_summary",
                resolvedModel.providerId(),
                resolvedModel.modelId(),
                promptContext,
                systemPrompt,
                request.messages(),
                null,
                List.of()
        );
        log.info("[TipSummaryChat] start provider={} model={} sessionId={} messageUid={}",
                resolvedModel.providerId(),
                resolvedModel.modelId(),
                promptContext == null ? "" : promptContext.sessionId(),
                promptContext == null ? "" : promptContext.messageUid());
        log.info("[TipSummaryChat] promptLocale={} acceptLanguage={} xAppLocale={}",
                locale,
                currentRequestHeader("Accept-Language"),
                currentRequestHeader("X-App-Locale"));
        log.info("[TipSummaryChat] systemPrompt:\n{}", systemPrompt);
        log.info("[TipSummaryChat] userPrompt:\n{}", prompt);
        try {
            ChatResponse response = resolvedModel.model().chat(request);
            llmDebugLogger.logResponse(requestId, "tip_summary", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, response, elapsedMillis(startNanos));
            if (tokenUsageRecorder != null) {
                tokenUsageRecorder.record(TokenUsageScene.TIP_SUMMARY, resolvedModel.providerId(), resolvedModel.modelId(),
                        promptContext == null ? "" : promptContext.sessionId(),
                        promptContext == null ? "" : promptContext.messageUid(), response);
            }
            String output = response.aiMessage() == null ? "" : response.aiMessage().text();
            TokenUsage usage = response.metadata() == null ? null : response.metadata().tokenUsage();
            Integer inputTokens = usage == null ? null : usage.inputTokenCount();
            Integer cachedInputTokens = TokenUsageCacheTokenExtractor.extractCachedInputTokens(usage);
            Integer outputTokens = usage == null ? null : usage.outputTokenCount();
            Integer totalTokens = usage == null ? null : usage.totalTokenCount();
            log.info("[TipSummaryChat] finish provider={} model={} outputLength={} inputTokens={} cachedInputTokens={} outputTokens={} totalTokens={}",
                    resolvedModel.providerId(),
                    resolvedModel.modelId(),
                    output == null ? 0 : output.length(),
                    inputTokens,
                    cachedInputTokens,
                    outputTokens,
                    totalTokens);
            return parseEvaluationResult(output, recentMessages, steps, finalResult);
        } catch (Exception ex) {
            llmDebugLogger.logError(requestId, "tip_summary", resolvedModel.providerId(), resolvedModel.modelId(),
                    promptContext, ex, elapsedMillis(startNanos));
            throw ex;
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static final class NoopLlmDebugLogger implements LlmDebugLogger {
        @Override
        public void logRequest(String requestId, String scene, String provider, String model, PromptLoader.PromptContext promptContext, String systemPrompt, List<ChatMessage> messages, ToolChoice toolChoice, List<String> toolNames) {
        }

        @Override
        public void logResponse(String requestId, String scene, String provider, String model, PromptLoader.PromptContext promptContext, ChatResponse response, long latencyMs) {
        }

        @Override
        public void logError(String requestId, String scene, String provider, String model, PromptLoader.PromptContext promptContext, Throwable error, long latencyMs) {
        }
    }

    private String buildEvaluationPrompt(List<RecentMessage> recentMessages,
                                         List<StepDigest> steps,
                                         String finalResult,
                                         Locale locale) {
        StringBuilder builder = new StringBuilder(loadUserPromptIntro(locale));
        builder.append("\n\n[recent_messages]\n");
        for (RecentMessage message : recentMessages) {
            builder.append("- role=").append(normalizeText(message.role()))
                    .append(" content=").append(normalizeText(message.content()))
                    .append('\n');
        }
        builder.append("\n[steps]\n");
        for (StepDigest step : steps) {
            builder.append("- round=").append(step.roundIndex())
                    .append(" step=").append(step.stepIndex())
                    .append(" title=").append(normalizeText(step.title()))
                    .append(" tool=").append(normalizeText(step.toolName()))
                    .append(" status=").append(normalizeText(step.status()))
                    .append(" retryCount=").append(step.retryCount())
                    .append(" doneCriteria=").append(normalizeText(step.doneCriteria()))
                    .append(" lastError=").append(normalizeText(step.lastError()))
                    .append('\n');
        }
        builder.append("\n[final_result]\n").append(normalizeText(finalResult));
        builder.append("\n\n[signals]\n");
        builder.append("- tool_call_count=").append(steps.size()).append('\n');
        builder.append("- retry_step_count=").append(steps.stream().filter(step -> step.retryCount() > 0).count()).append('\n');
        builder.append("- failed_step_count=").append(steps.stream().filter(step -> "FAILED".equalsIgnoreCase(step.status())).count()).append('\n');
        builder.append("- error_step_count=").append(steps.stream().filter(step -> hasMeaningfulText(step.lastError())).count()).append('\n');
        builder.append("- user_message_count=").append(recentMessages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()) && hasMeaningfulText(message.content()))
                .count()).append('\n');
        builder.append("- assistant_message_count=").append(recentMessages.stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.role()) && hasMeaningfulText(message.content()))
                .count()).append('\n');
        return builder.toString();
    }

    private Locale resolvePromptLocale() {
        Locale explicitLocale = parseLocaleHeader(currentRequestHeader("X-App-Locale"));
        if (explicitLocale != null) {
            return explicitLocale;
        }
        Locale acceptLanguageLocale = parseAcceptLanguageHeader(currentRequestHeader("Accept-Language"));
        if (acceptLanguageLocale != null) {
            return acceptLanguageLocale;
        }
        return LocaleContextHolder.getLocale();
    }

    private String currentRequestHeader(String name) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "";
        }
        String value = attributes.getRequest().getHeader(name);
        return value == null ? "" : value.trim();
    }

    private Locale parseLocaleHeader(String raw) {
        String normalized = normalizeText(raw);
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT).startsWith("zh") ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
    }

    private Locale parseAcceptLanguageHeader(String raw) {
        String normalized = normalizeText(raw);
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Locale.LanguageRange.parse(normalized).stream()
                    .sorted(Comparator.comparingDouble(Locale.LanguageRange::getWeight).reversed())
                    .map(Locale.LanguageRange::getRange)
                    .map(this::parseLocaleHeader)
                    .filter(locale -> locale != null && SUPPORTED_PROMPT_LOCALES.stream()
                            .anyMatch(supported -> supported.getLanguage().equals(locale.getLanguage())))
                    .findFirst()
                    .orElse(null);
        } catch (IllegalArgumentException ex) {
            return parseLocaleHeader(normalized);
        }
    }

    private String loadSystemPrompt(Locale locale) {
        if (promptTemplateService == null) {
            return DEFAULT_TIP_EVALUATION_SYSTEM_PROMPT;
        }
        return promptTemplateService.load(
                TIP_PROMPT_NAMESPACE,
                TIP_SYSTEM_TEMPLATE,
                locale,
                DEFAULT_TIP_EVALUATION_SYSTEM_PROMPT
        );
    }

    private String loadUserPromptIntro(Locale locale) {
        if (promptTemplateService == null) {
            return DEFAULT_TIP_EVALUATION_USER_PROMPT_INTRO;
        }
        return promptTemplateService.load(
                TIP_PROMPT_NAMESPACE,
                TIP_USER_INTRO_TEMPLATE,
                locale,
                DEFAULT_TIP_EVALUATION_USER_PROMPT_INTRO
        );
    }

    private TipEvaluationResult parseEvaluationResult(String rawOutput,
                                                      List<RecentMessage> recentMessages,
                                                      List<StepDigest> steps,
                                                      String finalResult) {
        String candidate = extractJsonCandidate(rawOutput);
        TipEvaluationPayload payload = candidate.isBlank()
                ? null
                : JsonUtil.fromJsonQuietly(candidate, TipEvaluationPayload.class).orElse(null);
        if (payload == null) {
            log.warn("[TipSummaryChat] unable to parse tip evaluation, fallback to local heuristic");
            return fallbackEvaluation(recentMessages, steps, finalResult);
        }

        String decision = normalizeText(payload.decision()).toLowerCase(Locale.ROOT);
        if ("reject".equals(decision)) {
            String reason = normalizeRejectReason(payload.reason());
            return TipEvaluationResult.reject(reason);
        }
        if (!"save".equals(decision)) {
            log.warn("[TipSummaryChat] unexpected tip decision={}, fallback to local heuristic", payload.decision());
            return fallbackEvaluation(recentMessages, steps, finalResult);
        }

        String content = normalizeText(payload.content());
        if (content.isBlank()) {
            content = buildFallbackBestPractice(recentMessages, steps, finalResult).sourceContent();
        }
        String title = normalizeText(payload.title());
        if (title.isBlank()) {
            title = buildTipTitle(content);
        }
        String summary = normalizeText(payload.summary());
        if (summary.isBlank()) {
            summary = buildTipSummary(content);
        }
        String reason = normalizeText(payload.reason());
        if (reason.isBlank()) {
            reason = "内容包含可复用步骤与经验，适合沉淀为锦囊";
        }
        return TipEvaluationResult.save(reason, title, truncateByChars(summary, 300), truncateByChars(content, 1000));
    }

    private TipEvaluationResult fallbackEvaluation(List<RecentMessage> recentMessages,
                                                   List<StepDigest> steps,
                                                   String finalResult) {
        BestPracticeTip bestPractice = buildFallbackBestPractice(recentMessages, steps, finalResult);
        long retryStepCount = steps.stream().filter(step -> step.retryCount() > 0).count();
        long failedStepCount = steps.stream().filter(step -> "FAILED".equalsIgnoreCase(step.status())).count();
        long errorStepCount = steps.stream().filter(step -> hasMeaningfulText(step.lastError())).count();
        long userMessageCount = recentMessages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()) && hasMeaningfulText(message.content()))
                .count();
        boolean hasRecovery = retryStepCount > 0 || failedStepCount > 0 || errorStepCount > 0;
        boolean hasReusableSignal = steps.size() >= 4 || retryStepCount >= 2 || userMessageCount >= 2 || hasRecovery;
        if (!hasReusableSignal) {
            return TipEvaluationResult.reject("这条内容更像一次结果展示，还没有沉淀出可复用的方法、流程或排错经验，暂时不建议保存为锦囊。");
        }
        return TipEvaluationResult.save(
                "包含可复用执行路径与经验，适合沉淀为锦囊",
                bestPractice.title(),
                bestPractice.summary(),
                bestPractice.sourceContent()
        );
    }

    private String extractJsonCandidate(String rawOutput) {
        String normalized = normalizeText(rawOutput);
        if (normalized.isBlank()) {
            return "";
        }
        int fencedStart = normalized.indexOf("```");
        if (fencedStart >= 0) {
            int fencedEnd = normalized.lastIndexOf("```");
            if (fencedEnd > fencedStart) {
                normalized = normalized.substring(fencedStart + 3, fencedEnd).trim();
                if (normalized.startsWith("json")) {
                    normalized = normalized.substring(4).trim();
                }
            }
        }
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return normalized.substring(start, end + 1).trim();
        }
        return normalized;
    }

    private BestPracticeTip buildFallbackBestPractice(List<RecentMessage> recentMessages,
                                                      List<StepDigest> steps,
                                                      String finalResult) {
        String goal = recentMessages.stream()
                .filter(item -> "user".equals(item.role()) && hasMeaningfulText(item.content()))
                .reduce((first, second) -> second)
                .map(item -> normalizeText(item.content()))
                .orElse("完成当前用户任务并产出可用结果");
        String stepPath = steps.stream()
                .limit(4)
                .map(step -> normalizeText(step.title()).isBlank()
                        ? normalizeText(step.toolName())
                        : normalizeText(step.title()))
                .filter(text -> !text.isBlank())
                .toList()
                .stream()
                .reduce((left, right) -> left + " -> " + right)
                .orElse("确认目标 -> 执行关键操作 -> 校验结果");
        String pitfalls = steps.stream()
                .filter(step -> step.retryCount() > 0 || hasMeaningfulText(step.lastError()) || "FAILED".equalsIgnoreCase(step.status()))
                .map(step -> hasMeaningfulText(step.lastError()) ? normalizeText(step.lastError()) : "步骤重试后才成功")
                .findFirst()
                .orElse("避免跳过前置校验，出现异常时先定位失败步骤再重试");
        String completion = normalizeText(finalResult);
        if (completion.isBlank()) {
            completion = "结果可直接交付且包含必要信息";
        } else {
            completion = abbreviate(completion, 80);
        }
        String summary = "目标：" + abbreviate(goal, 48)
                + "；路径：" + abbreviate(stepPath, 88)
                + "；避坑：" + abbreviate(pitfalls, 72)
                + "；完成判定：" + completion;
        summary = truncateByChars(summary, 300);
        return new BestPracticeTip(buildTipTitle(summary), summary, summary);
    }

    private String buildTipTitle(String content) {
        String normalized = normalizeText(content);
        if (normalized.isBlank()) {
            return "锦囊";
        }
        String firstLine = normalized.split("[。！？\\n]")[0].trim();
        if (firstLine.isBlank()) {
            firstLine = normalized;
        }
        return abbreviate(firstLine, 18);
    }

    private String buildTipSummary(String content) {
        String normalized = normalizeText(content);
        if (normalized.isBlank()) {
            return "围绕当前任务沉淀了可复用执行步骤与注意事项。";
        }
        return abbreviate(normalized, 80);
    }

    private String truncateByChars(String text, int maxLength) {
        String normalized = normalizeText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim();
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private boolean hasMeaningfulText(String text) {
        return !normalizeText(text).isBlank();
    }

    private String normalizeRejectReason(String reason) {
        String normalized = normalizeText(reason);
        if (normalized.isBlank()) {
            return "这条内容暂时不适合保存为锦囊。";
        }
        if (normalized.contains("单次") || normalized.contains("结果展示") || normalized.contains("缺失具体执行步骤")
                || normalized.contains("缺少可复用") || normalized.contains("方法论") || normalized.contains("信息不足")) {
            return "这条内容更像一次结果展示，还没有沉淀出可复用的方法、流程或排错经验，暂时不建议保存为锦囊。";
        }
        return normalized;
    }

    public record TipEvaluationResult(
            boolean shouldSave,
            String reason,
            String title,
            String summary,
            String content
    ) {
        public static TipEvaluationResult save(String reason, String title, String summary, String content) {
            return new TipEvaluationResult(true, reason, title, summary, content);
        }

        public static TipEvaluationResult reject(String reason) {
            return new TipEvaluationResult(false, reason, "", "", "");
        }
    }

    private record TipEvaluationPayload(
            String decision,
            String reason,
            String title,
            String summary,
            String content
    ) {
    }

    public record RecentMessage(String role, String content) {
    }

    public record StepDigest(int roundIndex,
                             int stepIndex,
                             String title,
                             String toolName,
                             String status,
                             int retryCount,
                             String doneCriteria,
                             String lastError) {
    }

    private record BestPracticeTip(String title, String summary, String sourceContent) {
    }
}
