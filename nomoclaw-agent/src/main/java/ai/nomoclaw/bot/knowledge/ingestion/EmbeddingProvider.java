package ai.nomoclaw.bot.knowledge.ingestion;

import java.util.List;

/**
 * Creates dense vectors using a configured model provider.
 */
public interface EmbeddingProvider {
    /**
     * Generates vectors and verifies every result has the requested dimension.
     */
    List<List<Float>> embed(List<String> texts, String providerId, String modelId, int expectedDimension);
}
