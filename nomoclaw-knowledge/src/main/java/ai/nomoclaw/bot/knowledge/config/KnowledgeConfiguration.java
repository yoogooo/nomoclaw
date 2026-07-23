package ai.nomoclaw.bot.knowledge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

/**
 * Configures knowledge-base ingestion workers.
 */
@Configuration
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeConfiguration {
    /**
     * Executor dedicated to parsing and vector indexing.
     */
    @Bean("knowledgeIngestionExecutor")
    public Executor knowledgeIngestionExecutor(KnowledgeProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getIngestion().getWorkerCount());
        executor.setMaxPoolSize(properties.getIngestion().getWorkerCount());
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("knowledge-ingestion-");
        executor.initialize();
        return executor;
    }

    /**
     * Scheduler for durable-job dispatching and lease heartbeats.
     */
    @Bean("knowledgeIngestionScheduler")
    public ThreadPoolTaskScheduler knowledgeIngestionScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("knowledge-ingestion-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    /**
     * Executor used to bound local reranker inference time independently from ingestion.
     */
    @Bean("knowledgeRerankExecutor")
    public Executor knowledgeRerankExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("knowledge-rerank-");
        executor.initialize();
        return executor;
    }
}
