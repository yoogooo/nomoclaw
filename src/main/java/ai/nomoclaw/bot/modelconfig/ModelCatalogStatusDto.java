package ai.nomoclaw.bot.modelconfig;

public record ModelCatalogStatusDto(
        String catalogVersion,
        String generatedAt,
        String source,
        boolean stale,
        String message
) {
}
