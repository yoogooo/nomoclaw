package ai.nomoclaw.bot.planner;

import ai.nomoclaw.bot.llm.config.LlmProperties;
import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.prompt.SkillPromptLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskPlannerTests {

    @TempDir
    Path tempDir;

    @Test
    void summarizeShouldFallbackWhenAiMessageTextIsNull() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setSystemPrompt("test system prompt");

        RuntimeChatModelResolver runtimeChatModelResolver = mock(RuntimeChatModelResolver.class);
        SkillPromptLoader skillPromptLoader = mock(SkillPromptLoader.class);
        when(skillPromptLoader.buildAgentSkillPrompt(any())).thenReturn("");

        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(null);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);

        RuntimeChatModelResolver.ResolvedModel resolvedModel = new RuntimeChatModelResolver.ResolvedModel(
                "openai",
                "gpt-5.4",
                chatModel,
                null
        );
        when(runtimeChatModelResolver.resolve(any())).thenReturn(resolvedModel);

        TaskPlanner taskPlanner = new TaskPlanner(llmProperties, runtimeChatModelResolver, skillPromptLoader);
        List<ChatMessage> memory = List.of(UserMessage.from("请总结"));
        PromptLoader.PromptContext promptContext = PromptLoader.PromptContext.forAgent(
                "session-1",
                "message-1",
                "web",
                "",
                "general_assistant",
                tempDir
        );

        Planner.SummaryResult result = assertDoesNotThrow(() -> taskPlanner.summarize(
                memory,
                "MODEL_RETURNED_EMPTY_ANSWER",
                2,
                6,
                promptContext,
                List.of()
        ));

        assertEquals("任务已停止。停止原因=MODEL_RETURNED_EMPTY_ANSWER，已执行轮次=2/6。", result.answer());
    }
}
