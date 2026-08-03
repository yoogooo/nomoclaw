package ai.nomoclaw.bot.knowledge.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public knowledge-base request and response models.
 */
public final class KnowledgeModels {
    private KnowledgeModels() {
    }

    public record StatusResponse(String status) {
    }

    public record CreateRequest(String name, String description, String embeddingProviderId, String embeddingModelId,
                                Integer embeddingDimension) {
    }

    public record UpdateRequest(String name, String description, Integer retrievalTopK, Double similarityThreshold) {
    }

    public record Base(String knowledgeBaseUid, String name, String description, String status,
                       String embeddingProviderId, String embeddingModelId, int embeddingDimension,
                       String vectorCollectionName, int documentCount, long chunkCount, LocalDateTime updatedTime) {
    }

    public record Document(String documentUid, String knowledgeBaseUid, String importBatchUid,
                           String displayName, String contentType,
                           long sizeBytes, String status, String failureCode, String failureMessage, int pageCount,
                           int chunkCount, String jobUid, String jobStatus, String stage, int progressPercent,
                           int attemptCount, int maxAttempts, int processedChunks, int totalChunks,
                           int processedPages, int totalPages, int cacheHitChunks, int cacheMissChunks,
                           List<String> parseWarnings, LocalDateTime nextRetryTime, boolean retryable,
                           LocalDateTime updatedTime) {
    }

    public record UploadFileResult(String fileName, String outcome, Document document, String errorCode,
                                   String errorMessage) {
    }

    public record UploadResult(String batchUid, List<Document> documents, List<UploadFileResult> items) {
    }

    public record BuildRequest(String parserMode, String chunkStrategy, Integer chunkSizeTokens, Integer chunkOverlapTokens,
                               PreprocessingRequest preprocessing) {
        public BuildRequest(String parserMode, Integer chunkSizeTokens, Integer chunkOverlapTokens) {
            this(parserMode, null, chunkSizeTokens, chunkOverlapTokens, null);
        }
    }

    public record PreprocessingRequest(Boolean enabled, PdfPreprocessingRequest pdf) {
    }

    public record PdfPreprocessingRequest(Boolean removeHeader, Boolean removeFooter, Boolean removeWatermark,
                                          Boolean removeTableOfContents) {
        public PdfPreprocessingRequest(Boolean removeHeader, Boolean removeFooter, Boolean removeWatermark) {
            this(removeHeader, removeFooter, removeWatermark, false);
        }
    }

    public record ImportItem(String itemUid, String fileName, String mode, String outcome, String status,
                             Document document, String errorCode, String errorMessage) {
    }

    public record ImportBatch(String batchUid, String knowledgeBaseUid, String status, String parserMode,
                              String chunkStrategy,
                              int chunkSizeTokens, int chunkOverlapTokens, String embeddingProviderId,
                              String embeddingModelId, int embeddingDimension, PreprocessingRequest preprocessing,
                              List<ImportItem> items, LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public record SearchRequest(String query, Integer topK) {
    }

    public record SearchDiagnostic(String code) {
    }

    public record SearchResponse(List<SearchHit> hits, List<SearchDiagnostic> diagnostics) {
    }

    public record SearchHit(String citationId, String knowledgeBaseUid, String knowledgeBaseName, String documentUid,
                            String documentName, Integer pageFrom, Integer pageTo, String sectionPath, String excerpt,
                            double score, String chunkUid, Double rrfScore, Double denseScore, Double bm25Score,
                            Double rerankScore,
                            List<String> retrievalSources) {
    }

    public record Chunk(String chunkUid, int chunkIndex, String content, int tokenCount, Integer pageFrom,
                        Integer pageTo, String sectionPath, Integer charStart, Integer charEnd, String status,
                        ChunkMetadata metadata) {
        public Chunk(String chunkUid, int chunkIndex, String content, int tokenCount, Integer pageFrom,
                     Integer pageTo, String sectionPath, Integer charStart, Integer charEnd, String status) {
            this(chunkUid, chunkIndex, content, tokenCount, pageFrom, pageTo, sectionPath, charStart, charEnd,
                    status, null);
        }
    }

    public record ChunkMetadata(String documentNodeUid, String document, String chapter, String title,
                                String section, String sectionPath, Integer pageFrom, Integer pageTo) {
    }

    public record DocumentNode(String nodeUid, String parentNodeUid, String type, int level, String code,
                               String title, String sectionPath, Integer pageFrom, Integer pageTo,
                               String detectionSource, double confidence, boolean indexable,
                               String nodeRole, int sourceOrder, double qualityScore, double parentConfidence,
                               String indexableReason,
                               List<DocumentNode> children) {
        public DocumentNode(String nodeUid, String parentNodeUid, String type, int level, String code,
                            String title, String sectionPath, Integer pageFrom, Integer pageTo,
                            String detectionSource, double confidence, boolean indexable,
                            List<DocumentNode> children) {
            this(nodeUid, parentNodeUid, type, level, code, title, sectionPath, pageFrom, pageTo,
                    detectionSource, confidence, indexable, type, 0, confidence, confidence,
                    indexable ? "" : "STRUCTURE_FILTERED", children);
        }
    }

    public record BindingRequest(List<String> include, List<String> exclude) {
    }

    public record Binding(List<String> effective, List<String> include, List<String> exclude) {
    }

    public record Retrieval(String retrievalUid, List<SearchHit> hits, String context) {
        public static Retrieval empty() {
            return new Retrieval("", List.of(), "");
        }
    }
}
