package ai.nomoclaw.bot.knowledge.config;

import java.time.Duration;

/**
 * Creates knowledge configuration values used by unit tests that do not load Spring configuration files.
 */
public final class KnowledgePropertiesTestSupport {
    private KnowledgePropertiesTestSupport() {
    }

    public static KnowledgeProperties properties() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.getUpload().setMaxFilesPerRequest(20);
        properties.getIngestion().setWorkerCount(2);
        properties.getIngestion().setLeaseSeconds(300);
        properties.getIngestion().setDispatchInterval(Duration.ofSeconds(1));
        properties.getIngestion().setInitialRetryDelay(Duration.ofSeconds(5));
        properties.getIngestion().setMaxRetryDelay(Duration.ofMinutes(5));
        properties.getIngestion().setMaxAttempts(3);
        properties.getIngestion().setEmbeddingBatchSize(32);
        properties.getChunking().setDefaultSizeTokens(500);
        properties.getChunking().setDefaultOverlapTokens(80);
        properties.getParsing().getPdf().getPreprocessing().setMaxHeaderLines(2);
        properties.getParsing().getPdf().getPreprocessing().setMaxFooterLines(2);
        properties.getParsing().getPdf().getPreprocessing().setRepeatedLineThresholdRatio(.5);
        properties.getParsing().getPdf().getPreprocessing().setWatermarkRepeatedThresholdRatio(.5);
        properties.getParsing().getPdf().getPreprocessing().setWatermarkCenterRegionRatio(.65);
        properties.getParsing().getPdf().getPreprocessing().setWatermarkMinTextLength(2);
        properties.getParsing().getPdf().getPreprocessing().setWatermarkMaxTextLength(80);
        properties.getParsing().getPdf().getPreprocessing().setWatermarkRotationThresholdDegrees(10);
        properties.getParsing().getPdf().getPreprocessing().setWatermarkMinFontSize(18);
        properties.getParsing().getPdf().getPreprocessing().setWatermarkNormalizeSpacing(true);
        properties.getEmbeddingCache().setEnabled(true);
        properties.getEmbeddingCache().setMaxEntries(100000);
        properties.getEmbeddingCache().setTtl(Duration.ofDays(30));
        properties.getEmbeddingCache().setCleanupInterval(Duration.ofHours(1));
        properties.getRetrieval().setDefaultTopK(8);
        properties.getRetrieval().setMaxContextTokens(6000);
        properties.getRetrieval().setDefaultSimilarityThreshold(.35);
        properties.getRetrieval().setMaxChunksPerDocument(3);
        properties.getRetrieval().getBm25().setEnabled(true);
        properties.getRetrieval().getBm25().setBackend("elasticsearch");
        properties.getRetrieval().getBm25().setStartupRebuildEnabled(false);
        properties.getRetrieval().getBm25().setCandidateLimit(40);
        properties.getRetrieval().getBm25().setRrfK(60);
        properties.getRetrieval().getBm25().getElasticsearch().setUrl("http://127.0.0.1:9200");
        properties.getRetrieval().getBm25().getElasticsearch().setApiKey("");
        properties.getRetrieval().getBm25().getElasticsearch().setTimeout(Duration.ofSeconds(10));
        properties.getRetrieval().getBm25().getElasticsearch().setIndexName("nomoclaw_knowledge_bm25");
        properties.getRetrieval().getRerank().setEnabled(true);
        properties.getRetrieval().getRerank().setWarmupEnabled(false);
        properties.getRetrieval().getRerank().setProvider("onnx");
        properties.getRetrieval().getRerank().setCandidateLimit(50);
        properties.getRetrieval().getRerank().setTimeout(Duration.ofSeconds(15));
        properties.getVector().getQdrant().setUrl("http://127.0.0.1:6333");
        properties.getVector().getQdrant().setApiKey("");
        properties.getVector().getQdrant().setTimeout(Duration.ofSeconds(10));
        return properties;
    }
}
