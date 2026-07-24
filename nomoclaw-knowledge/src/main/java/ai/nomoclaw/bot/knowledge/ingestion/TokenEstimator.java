package ai.nomoclaw.bot.knowledge.ingestion;

/**
 * Estimates embedding tokens without binding ingestion to a provider-specific tokenizer.
 */
public interface TokenEstimator {
    int estimate(String text);
}
