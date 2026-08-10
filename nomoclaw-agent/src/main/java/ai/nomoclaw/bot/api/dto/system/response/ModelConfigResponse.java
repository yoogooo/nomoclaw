package ai.nomoclaw.bot.api.dto.system.response;

import java.util.List;

public record ModelConfigResponse(
        List<Provider> providers
) {

    public record Provider(
            String id,
            String name,
            String protocol,
            boolean local,
            boolean requireApiKey,
            boolean freezeUrl,
            String baseUrl,
            String apiKey,
            boolean configured,
            String authStatus,
            String authMessage,
            String defaultModel,
            List<Model> models
    ) {
    }

    public record Model(
            String id,
            String name,
            String modelType,
            ModelCapabilities capabilities,
            Integer contextWindow,
            Integer maxInputTokens,
            Integer maxOutputTokens,
            boolean catalogMatched,
            String catalogSource
    ) {
    }

    public record ModelCapabilities(
            boolean toolCalling,
            boolean imageRecognition,
            boolean audioRecognition,
            boolean videoRecognition,
            boolean reasoning
    ) {
    }
}
