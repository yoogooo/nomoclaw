package ai.nomoclaw.bot.knowledge.app;

/**
 * Executes one ingestion job under an already claimed lease.
 */
@FunctionalInterface
public interface KnowledgeIngestionRunner {
    void runIngestion(String jobUid, String leaseToken);
}
