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

    ChatResponse reason(List<ChatMessage> memory,
                        List<ToolSpecification> toolSpecifications,
                        ToolChoice toolChoice,
                        PromptLoader.PromptContext promptContext);

    StreamReasonResult reasonStream(List<ChatMessage> memory,
                                    List<ToolSpecification> toolSpecifications,
                                    ToolChoice toolChoice,
                                    PromptLoader.PromptContext promptContext,
                                    Consumer<String> onDelta,
                                    Runnable onRetryReset);

    SummaryResult summarize(List<ChatMessage> memory,
                            String stopReason,
                            int roundsUsed,
                            int maxRounds,
                            PromptLoader.PromptContext promptContext,
                            List<ToolSpecification> toolSpecifications);
}
