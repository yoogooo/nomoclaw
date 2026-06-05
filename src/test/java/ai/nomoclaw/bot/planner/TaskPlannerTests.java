package ai.nomoclaw.bot.planner;

import ai.nomoclaw.bot.llm.config.LlmProperties;
import ai.nomoclaw.bot.llm.debug.LlmDebugLogger;
import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.prompt.SkillPromptLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
        LlmDebugLogger llmDebugLogger = mock(LlmDebugLogger.class);
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

        TaskPlanner taskPlanner = new TaskPlanner(llmProperties, runtimeChatModelResolver, skillPromptLoader, llmDebugLogger);
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

    @Test
    void reasonStreamShouldFallbackToNonStreamingWhenHandshakeFails() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setSystemPrompt("test system prompt");
        llmProperties.setMaxRetries(0);

        RuntimeChatModelResolver runtimeChatModelResolver = mock(RuntimeChatModelResolver.class);
        SkillPromptLoader skillPromptLoader = mock(SkillPromptLoader.class);
        LlmDebugLogger llmDebugLogger = mock(LlmDebugLogger.class);
        when(skillPromptLoader.buildAgentSkillPrompt(any())).thenReturn("");

        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn("fallback answer");
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);

        StreamingChatModel streamingChatModel = mock(StreamingChatModel.class);
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new SSLHandshakeException("Remote host terminated the handshake"));
            return null;
        }).when(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        RuntimeChatModelResolver.ResolvedModel resolvedModel = new RuntimeChatModelResolver.ResolvedModel(
                "dashscope",
                "deepseek-v4-flash",
                chatModel,
                streamingChatModel
        );
        when(runtimeChatModelResolver.resolve(any())).thenReturn(resolvedModel);

        TaskPlanner taskPlanner = new TaskPlanner(llmProperties, runtimeChatModelResolver, skillPromptLoader, llmDebugLogger);
        List<ChatMessage> memory = List.of(UserMessage.from("请继续"));
        PromptLoader.PromptContext promptContext = PromptLoader.PromptContext.forAgent(
                "session-1",
                "message-1",
                "web",
                "",
                "general_assistant",
                tempDir
        );

        Planner.StreamReasonResult result = assertDoesNotThrow(() -> taskPlanner.reasonStream(
                memory,
                List.of(),
                ToolChoice.AUTO,
                promptContext,
                null
        ));

        assertEquals("fallback answer", result.accumulatedText());
        assertEquals(false, result.streamed());
    }
}
