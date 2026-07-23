package ai.nomoclaw.bot.knowledge.app;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optional ingestion metrics that remain inert when no MeterRegistry is configured.
 */
@Component
public class KnowledgeIngestionMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger inFlight = new AtomicInteger();

    public KnowledgeIngestionMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.gauge("nomoclaw.knowledge.ingestion.in_flight", inFlight);
        }
    }

    public void started() {
        inFlight.incrementAndGet();
    }

    public void finished(String outcome, Duration duration) {
        inFlight.updateAndGet(value -> Math.max(0, value - 1));
        if (registry == null) return;
        registry.counter("nomoclaw.knowledge.ingestion.jobs", "outcome", outcome).increment();
        Timer.builder("nomoclaw.knowledge.ingestion.duration")
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }

    public void retried(String code) {
        if (registry != null) {
            registry.counter("nomoclaw.knowledge.ingestion.retries", "code", safeCode(code)).increment();
        }
    }

    private String safeCode(String code) {
        return code == null || code.isBlank() ? "UNKNOWN" : code;
    }
}
