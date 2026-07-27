package ai.nomoclaw.bot.knowledge.app;

import ai.nomoclaw.bot.knowledge.core.repository.KnowledgePersistenceRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Optional database-backed gauges for persisted import-builder state.
 */
@Component
public class KnowledgeImportMetrics {
    public KnowledgeImportMetrics(KnowledgePersistenceRepository persistence, ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Gauge.builder("nomoclaw.knowledge.import.draft_batches", persistence, this::draftCount)
                    .register(registry);
        }
    }

    private double draftCount(KnowledgePersistenceRepository persistence) {
        try {
            Integer count = persistence.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_import_batch WHERE status='DRAFT'", Integer.class);
            return count == null ? 0 : count;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
