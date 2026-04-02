package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.planner.RuntimeChatModelResolver;
import ai.nomoclaw.bot.prompt.PromptLoader;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class TipSummaryChatService {

    private static final String TIP_SUMMARY_SYSTEM_PROMPT = """
            你是一个“最佳实践提炼器”。
            你的唯一任务：根据输入上下文输出高质量经验总结文本。
            严格要求：
            1) 仅输出纯文本，不要 JSON、不要代码块、不要额外前后缀；
            2) 文本长度控制在 1000 中文字符以内；
            3) 必须包含：目标、关键步骤顺序、常见失败/绕路点、完成判定；
            4) 内容要可复用、可执行，避免寒暄和空话。
            """;
    private static final String TIP_SUMMARY_USER_PROMPT_INTRO = """
            请基于最近对话、执行步骤和最终结果，提炼一个下次可直接复用的最佳实践。
            只输出纯文本总结，不要 JSON、不要代码块、不要额外说明。
            要求：
            1) 输出长度 <=1000中文字符；
            2) 必须包含：目标、关键步骤顺序、常见失败/绕路点、完成判定；
            3) 禁止寒暄、禁止解释生成过程。
            """;

    private final RuntimeChatModelResolver runtimeChatModelResolver;

    public TipSummaryChatService(RuntimeChatModelResolver runtimeChatModelResolver) {
        this.runtimeChatModelResolver = runtimeChatModelResolver;
    }

    public String summarize(PromptLoader.PromptContext promptContext,
                            List<RecentMessage> recentMessages,
                            List<StepDigest> steps,
                            String finalResult) {
        String prompt = buildUserPrompt(recentMessages, steps, finalResult);
        RuntimeChatModelResolver.ResolvedModel resolvedModel = runtimeChatModelResolver.resolve(promptContext);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(TIP_SUMMARY_SYSTEM_PROMPT),
                        UserMessage.from(prompt)
                ))
                .build();
        log.info("[TipSummaryChat] start provider={} model={} sessionId={} messageUid={}",
                resolvedModel.providerId(),
                resolvedModel.modelId(),
                promptContext == null ? "" : promptContext.sessionId(),
                promptContext == null ? "" : promptContext.messageUid());
        ChatResponse response = resolvedModel.model().chat(request);
        String output = response.aiMessage() == null ? "" : response.aiMessage().text();
        log.info("[TipSummaryChat] finish provider={} model={} outputLength={}",
                resolvedModel.providerId(),
                resolvedModel.modelId(),
                output == null ? 0 : output.length());
        return output == null ? "" : output.trim();
    }

    private String buildUserPrompt(List<RecentMessage> recentMessages,
                                   List<StepDigest> steps,
                                   String finalResult) {
        StringBuilder builder = new StringBuilder(TIP_SUMMARY_USER_PROMPT_INTRO);
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
        return builder.toString();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
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
}
