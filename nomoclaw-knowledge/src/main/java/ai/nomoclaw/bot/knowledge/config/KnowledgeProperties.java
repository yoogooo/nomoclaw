package ai.nomoclaw.bot.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Knowledge-base runtime configuration.
 */
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {
    private boolean enabled = true;
    private Path storageRoot;
    private final Upload upload = new Upload();
    private final Ingestion ingestion = new Ingestion();
    private final Chunking chunking = new Chunking();
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

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Vector getVector() {
        return vector;
    }

    public static class Upload {
        private int maxFilesPerRequest = 20;

        public int getMaxFilesPerRequest() {
            return maxFilesPerRequest;
        }

        public void setMaxFilesPerRequest(int value) {
            maxFilesPerRequest = value;
        }
    }

    public static class Ingestion {
        private int workerCount = 2;
        private int maxAttempts = 3;
        private int embeddingBatchSize = 32;

        public int getWorkerCount() {
            return workerCount;
        }

        public void setWorkerCount(int value) {
            workerCount = value;
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
        private int defaultSizeTokens = 500;
        private int defaultOverlapTokens = 80;

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

    public static class Retrieval {
        private int defaultTopK = 8;
        private int maxContextTokens = 6000;
        private double defaultSimilarityThreshold = .35;
        private int maxChunksPerDocument = 3;
        private final Bm25 bm25 = new Bm25();

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
    }

    public static class Bm25 {
        private boolean enabled = true;
        private Path indexPath;
        private int candidateLimit = 40;
        private int rrfK = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public Path getIndexPath() {
            return indexPath;
        }

        public void setIndexPath(Path value) {
            indexPath = value;
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
    }

    public static class Vector {
        private final Qdrant qdrant = new Qdrant();

        public Qdrant getQdrant() {
            return qdrant;
        }
    }

    public static class Qdrant {
        private String url = "http://127.0.0.1:6333";
        private String apiKey = "";
        private Duration timeout = Duration.ofSeconds(10);

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
