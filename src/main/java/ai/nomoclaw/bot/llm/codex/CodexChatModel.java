package ai.nomoclaw.bot.llm.codex;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.net.http.HttpClient;
import java.time.Duration;

public class CodexChatModel implements ChatModel {

    private final CodexApiClient client;
    private final String modelName;

    public CodexChatModel(HttpClient httpClient,
                          CodexTokenProvider tokenProvider,
                          String baseUrl,
                          String modelName,
                          Duration timeout) {
        this.client = new CodexApiClient(httpClient, tokenProvider, baseUrl, timeout);
        this.modelName = trim(modelName).isBlank() ? "gpt-5.4" : trim(modelName);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String effectiveModel = trim(request.modelName()).isBlank() ? modelName : trim(request.modelName());
        return client.chat(request, effectiveModel);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.builder()
                .modelName(modelName)
                .build();
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
