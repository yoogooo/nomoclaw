package ai.nomoclaw.bot.knowledge.config;

import ai.nomoclaw.bot.knowledge.app.KnowledgeService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts the one-time migration from legacy shared collections after Flyway has completed.
 */
@Component
public class KnowledgeCollectionMigrationRunner {
    private final KnowledgeService knowledgeService;

    public KnowledgeCollectionMigrationRunner(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * Backfills the persisted collection name for knowledge bases created before V4.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        knowledgeService.migrateLegacyCollections();
    }
}
