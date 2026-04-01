package ai.nomoclaw.bot.planner;

import ai.nomoclaw.bot.prompt.PromptLoader;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;

public interface Planner {

    record SummaryResult(String answer, ChatResponse response) {
    }

    ChatResponse reason(List<ChatMessage> memory,
                        List<ToolSpecification> toolSpecifications,
                        ToolChoice toolChoice,
                        PromptLoader.PromptContext promptContext);

    SummaryResult summarize(List<ChatMessage> memory,
                            String stopReason,
                            int roundsUsed,
                            int maxRounds,
                            PromptLoader.PromptContext promptContext,
                            List<ToolSpecification> toolSpecifications);
}
