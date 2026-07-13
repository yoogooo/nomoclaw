package ai.nomoclaw.bot.llm.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Locale;
import java.util.Objects;

@Configuration
@Slf4j
public class ChatModelConfig {

    @Bean(name = "agentChatModel")
    @Lazy
    public ChatModel agentChatModel(LlmProperties llmProperties) {
        String provider = llmProperties.getProvider() == null ? "codex" : llmProperties.getProvider().trim().toLowerCase(Locale.ROOT);
        var timeout = llmProperties.getTimeout();
        var maxRetries = llmProperties.getMaxRetries();
        return switch (provider) {
//            case "codex" -> {
//                //
//            }
            case "ollama" -> {
                log.info("[LLM] provider=ollama model={} baseUrl={}", llmProperties.getOllama().getModelName(), llmProperties.getOllama().getBaseUrl());
                yield OllamaChatModel.builder()
                        .baseUrl(llmProperties.getOllama().getBaseUrl())
                        .modelName(llmProperties.getOllama().getModelName())
                        .timeout(timeout)
                        .maxRetries(maxRetries)
                        .build();
            }
            case "qwen" -> {
                log.info("[LLM] provider=qwen model={} baseUrl={}",
                        llmProperties.getQwen().getModelName(),
                        llmProperties.getQwen().getBaseUrl());
                yield openAiCompatibleModel(
                        "qwen",
                        llmProperties.getQwen().getApiKey(),
                        llmProperties.getQwen().getBaseUrl(),
                        llmProperties.getQwen().getModelName(),
                        timeout,
                        maxRetries,
                        "DashScope API key is required when provider=qwen"
                );
            }
            case "gemini" -> {
                log.info("[LLM] provider=gemini model={} baseUrl={}",
                        llmProperties.getGemini().getModelName(),
                        llmProperties.getGemini().getBaseUrl());
                yield openAiCompatibleModel(
                        "gemini",
                        llmProperties.getGemini().getApiKey(),
                        llmProperties.getGemini().getBaseUrl(),
                        llmProperties.getGemini().getModelName(),
                        timeout,
                        maxRetries,
                        "Gemini API key is required when provider=gemini"
                );
            }
            case "kimi" -> {
                log.info("[LLM] provider=kimi model={} baseUrl={}",
                        llmProperties.getKimi().getModelName(),
                        llmProperties.getKimi().getBaseUrl());
                yield openAiCompatibleModel(
                        "kimi",
                        llmProperties.getKimi().getApiKey(),
                        llmProperties.getKimi().getBaseUrl(),
                        llmProperties.getKimi().getModelName(),
                        timeout,
                        maxRetries,
                        "Kimi API key is required when provider=kimi"
                );
            }
            case "minimax" -> {
                log.info("[LLM] provider=minimax model={} baseUrl={}",
                        llmProperties.getMinimax().getModelName(),
                        llmProperties.getMinimax().getBaseUrl());
                yield openAiCompatibleModel(
                        "minimax",
                        llmProperties.getMinimax().getApiKey(),
                        llmProperties.getMinimax().getBaseUrl(),
                        llmProperties.getMinimax().getModelName(),
                        timeout,
                        maxRetries,
                        "MiniMax API key is required when provider=minimax"
                );
            }
            default -> throw new IllegalArgumentException("Unsupported llm.provider: " + provider);
        };
    }

    private OpenAiChatModel openAiCompatibleModel(String provider,
                                                  String apiKey,
                                                  String baseUrl,
                                                  String modelName,
                                                  java.time.Duration timeout,
                                                  Integer maxRetries,
                                                  String missingApiKeyMessage) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL is required when provider=" + provider);
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Model name is required when provider=" + provider);
        }
        return OpenAiChatModel.builder()
                .apiKey(Objects.requireNonNull(apiKey, missingApiKeyMessage))
                .baseUrl(baseUrl.trim())
                .modelName(modelName.trim())
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
    }
}
