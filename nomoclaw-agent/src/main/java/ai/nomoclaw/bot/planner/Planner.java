package ai.nomoclaw.bot.planner;

import ai.nomoclaw.bot.prompt.PromptLoader;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.function.Consumer;

public interface Planner {

    record SummaryResult(String answer, ChatResponse response) {
    }

    record StreamReasonResult(ChatResponse response, String accumulatedText, boolean streamed) {
    }

    record RetryNotice(int retryIndex, int maxRetries, long retryDelaySeconds, String reason) {
    }

    ChatResponse reason(List<ChatMessage> memory,
                        List<ToolSpecification> toolSpecifications,
                        ToolChoice toolChoice,
                        PromptLoader.PromptContext promptContext);

    StreamReasonResult reasonStream(List<ChatMessage> memory,
                                    List<ToolSpecification> toolSpecifications,
                                    ToolChoice toolChoice,
                                    PromptLoader.PromptContext promptContext,
                                    Consumer<String> onDelta,
                                    Runnable onRetryReset,
                                    Consumer<RetryNotice> onRetry);

    default StreamReasonResult reasonStream(List<ChatMessage> memory,
                                             List<ToolSpecification> toolSpecifications,
                                             ToolChoice toolChoice,
                                             PromptLoader.PromptContext promptContext,
                                             int roundIndex,
                                             Consumer<String> onDelta,
                                             Runnable onRetryReset,
                                             Consumer<RetryNotice> onRetry) {
        return reasonStream(memory, toolSpecifications, toolChoice, promptContext, onDelta, onRetryReset, onRetry);
    }

    SummaryResult summarize(List<ChatMessage> memory,
                            String stopReason,
                            int roundsUsed,
                            int maxRounds,
                            PromptLoader.PromptContext promptContext,
                            List<ToolSpecification> toolSpecifications);
}
