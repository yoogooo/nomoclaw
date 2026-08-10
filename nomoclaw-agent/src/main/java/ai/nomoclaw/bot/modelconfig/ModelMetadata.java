package ai.nomoclaw.bot.modelconfig;

import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;

public record ModelMetadata(
        String providerId,
        String modelId,
        String displayName,
        String modelType,
        ModelConfigDto.ModelCapabilities capabilities,
        Integer contextWindowTokens,
        Integer maxInputTokens,
        Integer maxOutputTokens,
        boolean matched,
        String source,
        String confidence
) {
}
