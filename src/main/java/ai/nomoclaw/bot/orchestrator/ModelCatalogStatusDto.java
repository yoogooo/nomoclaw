package ai.nomoclaw.bot.orchestrator;

public record ModelCatalogStatusDto(
        String catalogVersion,
        String generatedAt,
        String source,
        boolean stale,
        String message
) {
}
