package ai.nomoclaw.bot.knowledge.app;

import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeImportBatchRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Optional database-backed gauges for persisted import-builder state.
 */
@Component
public class KnowledgeImportMetrics {
    public KnowledgeImportMetrics(KnowledgeImportBatchRepository batchRepository, ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Gauge.builder("nomoclaw.knowledge.import.draft_batches", batchRepository, this::draftCount)
                    .register(registry);
        }
    }

    private double draftCount(KnowledgeImportBatchRepository batchRepository) {
        try {
            return batchRepository.countDraft();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
