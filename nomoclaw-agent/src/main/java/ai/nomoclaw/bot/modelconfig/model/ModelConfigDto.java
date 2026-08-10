package ai.nomoclaw.bot.modelconfig.model;

import java.util.List;

public record ModelConfigDto(
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
        public static ModelCapabilities none() {
            return new ModelCapabilities(false, false, false, false, false);
        }
    }
}
