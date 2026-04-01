package ai.nomoclaw.bot.api;

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
            String defaultModel,
            List<Model> models
    ) {
    }

    public record Model(
            String id,
            String name,
            List<String> capabilities,
            boolean reasoning,
            Integer contextWindow,
            Integer maxInputTokens,
            Integer maxOutputTokens,
            UploadPolicy uploadPolicy
    ) {
    }

    public record UploadPolicy(
            boolean enabled,
            List<String> allowedMimeGroups,
            Integer maxFilesPerMessage,
            Integer maxImagesPerMessage,
            boolean singleMimeGroupOnly,
            boolean allowMixedImageAndFile
    ) {
    }
}
