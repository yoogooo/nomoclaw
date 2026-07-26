package ai.nomoclaw.bot.knowledge.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.net.URI;
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

    /**
     * Shared Elasticsearch low-level REST client for lexical BM25 indexing and retrieval.
     */
    @Bean(destroyMethod = "close")
    public Rest5Client knowledgeElasticsearchRestClient(KnowledgeProperties properties) {
        KnowledgeProperties.Elasticsearch config = properties.getRetrieval().getBm25().getElasticsearch();
        Rest5ClientBuilder builder = Rest5Client.builder(URI.create(config.getUrl()));
        if (!config.getApiKey().isBlank()) {
            Header[] headers = new Header[]{new BasicHeader("Authorization", "ApiKey " + config.getApiKey())};
            builder.setDefaultHeaders(headers);
        }
        return builder.build();
    }

    /**
     * Shared official Elasticsearch Java client for lexical BM25 indexing and retrieval.
     */
    @Bean
    public ElasticsearchClient knowledgeElasticsearchClient(Rest5Client knowledgeElasticsearchRestClient) {
        Rest5ClientTransport transport = new Rest5ClientTransport(
                knowledgeElasticsearchRestClient,
                new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
