package ai.nomoclaw.bot.llm.codex;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.net.http.HttpClient;
import java.time.Duration;

public class CodexStreamingChatModel implements StreamingChatModel {

    private final CodexApiClient client;
    private final String modelName;

    public CodexStreamingChatModel(HttpClient httpClient,
                                   CodexTokenProvider tokenProvider,
                                   String baseUrl,
                                   String modelName,
                                   Duration timeout) {
        this.client = new CodexApiClient(httpClient, tokenProvider, baseUrl, timeout);
        this.modelName = trim(modelName).isBlank() ? "gpt-5.5" : trim(modelName);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        String effectiveModel = trim(request.modelName()).isBlank() ? modelName : trim(request.modelName());
        client.stream(request, effectiveModel, handler);
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
