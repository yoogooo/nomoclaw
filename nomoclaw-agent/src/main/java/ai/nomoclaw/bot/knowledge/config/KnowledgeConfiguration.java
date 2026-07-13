package ai.nomoclaw.bot.knowledge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
}
