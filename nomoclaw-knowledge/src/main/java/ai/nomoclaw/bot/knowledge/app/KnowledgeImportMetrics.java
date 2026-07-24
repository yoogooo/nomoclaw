package ai.nomoclaw.bot.knowledge.app;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Optional database-backed gauges for persisted import-builder state.
 */
@Component
public class KnowledgeImportMetrics {
    public KnowledgeImportMetrics(JdbcTemplate jdbc, ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Gauge.builder("nomoclaw.knowledge.import.draft_batches", jdbc, this::draftCount)
                    .register(registry);
        }
    }

    private double draftCount(JdbcTemplate jdbc) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_import_batch WHERE status='DRAFT'", Integer.class);
            return count == null ? 0 : count;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
