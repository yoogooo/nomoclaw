package ai.nomoclaw.bot.api;

public record ModelCatalogStatusResponse(
        String catalogVersion,
        String generatedAt,
        String source,
        boolean stale,
        String message
) {
}
