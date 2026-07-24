package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

/**
 * Periodically enforces persistent embedding-cache TTL and entry limits.
 */
@Component
public class EmbeddingCacheJanitor implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingCacheJanitor.class);

    private final EmbeddingCacheStore cache;
    private final KnowledgeProperties properties;
    private final ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> future;

    public EmbeddingCacheJanitor(EmbeddingCacheStore cache, KnowledgeProperties properties,
                                 @Qualifier("knowledgeIngestionScheduler") ThreadPoolTaskScheduler scheduler) {
        this.cache = cache;
        this.properties = properties;
        this.scheduler = scheduler;
    }

    /**
     * Starts cleanup only after Flyway migrations have completed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.getEmbeddingCache().isEnabled() || future != null) return;
        future = scheduler.scheduleWithFixedDelay(this::cleanupSafely,
                properties.getEmbeddingCache().getCleanupInterval());
    }

    @Override
    public void destroy() {
        if (future != null) future.cancel(false);
    }

    private void cleanupSafely() {
        try {
            cache.cleanup();
        } catch (Exception ex) {
            log.warn("[KnowledgeEmbeddingCache] cleanup failed", ex);
        }
    }
}
