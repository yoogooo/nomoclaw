package ai.nomoclaw.bot.llm.debug;

import ai.nomoclaw.bot.prompt.PromptLoader;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;

/**
 * LLM 调试日志接口。
 */
public interface LlmDebugLogger {

    void logRequest(String requestId,
                    String scene,
                    String provider,
                    String model,
                    PromptLoader.PromptContext promptContext,
                    String systemPrompt,
                    List<ChatMessage> messages,
                    ToolChoice toolChoice,
                    List<String> toolNames);

    void logResponse(String requestId,
                     String scene,
                     String provider,
                     String model,
                     PromptLoader.PromptContext promptContext,
                     ChatResponse response,
                     long latencyMs);

    void logError(String requestId,
                  String scene,
                  String provider,
                  String model,
                  PromptLoader.PromptContext promptContext,
                  Throwable error,
                  long latencyMs);
}

