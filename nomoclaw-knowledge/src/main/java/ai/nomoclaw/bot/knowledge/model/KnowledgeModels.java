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

    public record Document(String documentUid, String knowledgeBaseUid, String displayName, String contentType,
                           long sizeBytes, String status, String failureCode, String failureMessage, int pageCount,
                           int chunkCount, String jobUid, String jobStatus, String stage, int progressPercent,
                           int attemptCount, int maxAttempts, int processedChunks, int totalChunks,
                           LocalDateTime nextRetryTime, boolean retryable, LocalDateTime updatedTime) {
    }

    public record UploadFileResult(String fileName, String outcome, Document document, String errorCode,
                                   String errorMessage) {
    }

    public record UploadResult(List<Document> documents, List<UploadFileResult> items) {
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
