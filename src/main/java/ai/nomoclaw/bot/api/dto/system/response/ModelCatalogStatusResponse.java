package ai.nomoclaw.bot.api.dto.system.response;

public record ModelCatalogStatusResponse(
        String catalogVersion,
        String generatedAt,
        String source,
        boolean stale,
        String message
) {
}
