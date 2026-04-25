package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;

import java.util.List;

public record ModelMetadata(
        String providerId,
        String modelId,
        String displayName,
        List<String> inputModalities,
        List<String> outputModalities,
        boolean reasoning,
        Integer contextWindowTokens,
        Integer maxInputTokens,
        Integer maxOutputTokens,
        ModelConfigDto.UploadPolicy uploadPolicy,
        boolean matched,
        String source,
        String confidence
) {
}
