package ai.nomoclaw.bot.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge-base runtime configuration.
 */
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {
    private boolean enabled;
    private Path storageRoot;
    private final Upload upload = new Upload();
    private final Ingestion ingestion = new Ingestion();
    private final Chunking chunking = new Chunking();
    private final Parsing parsing = new Parsing();
    private final EmbeddingCache embeddingCache = new EmbeddingCache();
    private final Retrieval retrieval = new Retrieval();
    private final Vector vector = new Vector();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public Upload getUpload() {
        return upload;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Chunking getChunking() {
        return chunking;
    }

    public Parsing getParsing() {
        return parsing;
    }

    public EmbeddingCache getEmbeddingCache() {
        return embeddingCache;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Vector getVector() {
        return vector;
    }

    public static class Upload {
        private int maxFilesPerRequest;

        public int getMaxFilesPerRequest() {
            return maxFilesPerRequest;
        }

        public void setMaxFilesPerRequest(int value) {
            maxFilesPerRequest = value;
        }
    }

    public static class Ingestion {
        private int workerCount;
        private int leaseSeconds;
        private Duration dispatchInterval;
        private Duration initialRetryDelay;
        private Duration maxRetryDelay;
        private int maxAttempts;
        private int embeddingBatchSize;

        public int getWorkerCount() {
            return workerCount;
        }

        public void setWorkerCount(int value) {
            workerCount = value;
        }

        public int getLeaseSeconds() {
            return leaseSeconds;
        }

        public void setLeaseSeconds(int value) {
            leaseSeconds = value;
        }

        public Duration getDispatchInterval() {
            return dispatchInterval;
        }

        public void setDispatchInterval(Duration value) {
            dispatchInterval = value;
        }

        public Duration getInitialRetryDelay() {
            return initialRetryDelay;
        }

        public void setInitialRetryDelay(Duration value) {
            initialRetryDelay = value;
        }

        public Duration getMaxRetryDelay() {
            return maxRetryDelay;
        }

        public void setMaxRetryDelay(Duration value) {
            maxRetryDelay = value;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int value) {
            maxAttempts = value;
        }

        public int getEmbeddingBatchSize() {
            return embeddingBatchSize;
        }

        public void setEmbeddingBatchSize(int value) {
            embeddingBatchSize = value;
        }
    }

    public static class Chunking {
        private int defaultSizeTokens;
        private int defaultOverlapTokens;

        public int getDefaultSizeTokens() {
            return defaultSizeTokens;
        }

        public void setDefaultSizeTokens(int value) {
            defaultSizeTokens = value;
        }

        public int getDefaultOverlapTokens() {
            return defaultOverlapTokens;
        }

        public void setDefaultOverlapTokens(int value) {
            defaultOverlapTokens = value;
        }
    }

    public static class Parsing {
        private final Pdf pdf = new Pdf();

        public Pdf getPdf() {
            return pdf;
        }
    }

    public static class Pdf {
        private final PdfPreprocessing preprocessing = new PdfPreprocessing();

        public PdfPreprocessing getPreprocessing() {
            return preprocessing;
        }
    }

    public static class PdfPreprocessing {
        private int maxHeaderLines;
        private int maxFooterLines;
        private double repeatedLineThresholdRatio;
        private List<String> headerLines = new ArrayList<>();
        private List<String> footerLines = new ArrayList<>();
        private List<String> watermarkLines = new ArrayList<>();

        public int getMaxHeaderLines() {
            return maxHeaderLines;
        }

        public void setMaxHeaderLines(int value) {
            maxHeaderLines = value;
        }

        public int getMaxFooterLines() {
            return maxFooterLines;
        }

        public void setMaxFooterLines(int value) {
            maxFooterLines = value;
        }

        public double getRepeatedLineThresholdRatio() {
            return repeatedLineThresholdRatio;
        }

        public void setRepeatedLineThresholdRatio(double value) {
            repeatedLineThresholdRatio = value;
        }

        public List<String> getHeaderLines() {
            return headerLines;
        }

        public void setHeaderLines(List<String> value) {
            headerLines = value == null ? new ArrayList<>() : value;
        }

        public List<String> getFooterLines() {
            return footerLines;
        }

        public void setFooterLines(List<String> value) {
            footerLines = value == null ? new ArrayList<>() : value;
        }

        public List<String> getWatermarkLines() {
            return watermarkLines;
        }

        public void setWatermarkLines(List<String> value) {
            watermarkLines = value == null ? new ArrayList<>() : value;
        }
    }

    public static class EmbeddingCache {
        private boolean enabled;
        private int maxEntries;
        private Duration ttl;
        private Duration cleanupInterval;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int value) {
            maxEntries = value;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration value) {
            ttl = value;
        }

        public Duration getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(Duration value) {
            cleanupInterval = value;
        }
    }

    public static class Retrieval {
        private int defaultTopK;
        private int maxContextTokens;
        private double defaultSimilarityThreshold;
        private int maxChunksPerDocument;
        private final Bm25 bm25 = new Bm25();
        private final Rerank rerank = new Rerank();

        public int getDefaultTopK() {
            return defaultTopK;
        }

        public void setDefaultTopK(int value) {
            defaultTopK = value;
        }

        public int getMaxContextTokens() {
            return maxContextTokens;
        }

        public void setMaxContextTokens(int value) {
            maxContextTokens = value;
        }

        public double getDefaultSimilarityThreshold() {
            return defaultSimilarityThreshold;
        }

        public void setDefaultSimilarityThreshold(double value) {
            defaultSimilarityThreshold = value;
        }

        public int getMaxChunksPerDocument() {
            return maxChunksPerDocument;
        }

        public void setMaxChunksPerDocument(int value) {
            maxChunksPerDocument = value;
        }

        public Bm25 getBm25() {
            return bm25;
        }

        public Rerank getRerank() {
            return rerank;
        }
    }

    public static class Bm25 {
        private boolean enabled;
        private String backend;
        private Path indexPath;
        private boolean startupRebuildEnabled;
        private int candidateLimit;
        private int rrfK;
        private final Elasticsearch elasticsearch = new Elasticsearch();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public String getBackend() {
            return backend;
        }

        public void setBackend(String value) {
            backend = value;
        }

        public Path getIndexPath() {
            return indexPath;
        }

        public void setIndexPath(Path value) {
            indexPath = value;
        }

        public boolean isStartupRebuildEnabled() {
            return startupRebuildEnabled;
        }

        public void setStartupRebuildEnabled(boolean value) {
            startupRebuildEnabled = value;
        }

        public int getCandidateLimit() {
            return candidateLimit;
        }

        public void setCandidateLimit(int value) {
            candidateLimit = value;
        }

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int value) {
            rrfK = value;
        }

        public Elasticsearch getElasticsearch() {
            return elasticsearch;
        }
    }

    public static class Elasticsearch {
        private String url;
        private String apiKey;
        private Duration timeout;
        private String indexName;

        public String getUrl() {
            return url;
        }

        public void setUrl(String value) {
            url = value;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String value) {
            apiKey = value;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration value) {
            timeout = value;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String value) {
            indexName = value;
        }
    }

    public static class Rerank {
        private boolean enabled;
        private boolean warmupEnabled;
        private String provider;
        private Path modelPath;
        private Path tokenizerPath;
        private int candidateLimit;
        private Duration timeout;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public boolean isWarmupEnabled() {
            return warmupEnabled;
        }

        public void setWarmupEnabled(boolean value) {
            warmupEnabled = value;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String value) {
            provider = value;
        }

        public Path getModelPath() {
            return modelPath;
        }

        public void setModelPath(Path value) {
            modelPath = value;
        }

        public Path getTokenizerPath() {
            return tokenizerPath;
        }

        public void setTokenizerPath(Path value) {
            tokenizerPath = value;
        }

        public int getCandidateLimit() {
            return candidateLimit;
        }

        public void setCandidateLimit(int value) {
            candidateLimit = value;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration value) {
            timeout = value;
        }
    }

    public static class Vector {
        private final Qdrant qdrant = new Qdrant();

        public Qdrant getQdrant() {
            return qdrant;
        }
    }

    public static class Qdrant {
        private String url;
        private String apiKey;
        private Duration timeout;

        public String getUrl() {
            return url;
        }

        public void setUrl(String value) {
            url = value;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String value) {
            apiKey = value;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration value) {
            timeout = value;
        }
    }
}
