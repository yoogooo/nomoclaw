package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeImportBatchEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportBatchMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Repository for knowledge import batches.
 */
@Repository
public class KnowledgeImportBatchRepository extends CrudRepository<KnowledgeImportBatchMapper, KnowledgeImportBatchEntity> {

    /** Finds a batch within its knowledge base. */
    public KnowledgeImportBatchEntity findByBaseAndUid(String knowledgeBaseUid, String batchUid) {
        return lambdaQuery().eq(KnowledgeImportBatchEntity::getKnowledgeBaseUid, knowledgeBaseUid)
                .eq(KnowledgeImportBatchEntity::getBatchUid, batchUid).one();
    }

    /** Counts draft import batches for metrics. */
    public long countDraft() {
        return lambdaQuery().eq(KnowledgeImportBatchEntity::getStatus, "DRAFT").count();
    }

    /** Finds one batch by business UID. */
    public KnowledgeImportBatchEntity findByUid(String batchUid) {
        return lambdaQuery().eq(KnowledgeImportBatchEntity::getBatchUid, batchUid).one();
    }

    /** Claims a draft batch for building with the selected configuration. */
    public boolean claimBuilding(String batchUid, String parserMode, String chunkStrategy,
                                 int chunkSizeTokens, int chunkOverlapTokens,
                                 String providerId, String modelId, int dimension, String modelFingerprint,
                                 String preprocessingConfig, String configHash, LocalDateTime updatedTime) {
        return lambdaUpdate().eq(KnowledgeImportBatchEntity::getBatchUid, batchUid)
                .eq(KnowledgeImportBatchEntity::getStatus, "DRAFT")
                .set(KnowledgeImportBatchEntity::getStatus, "BUILDING")
                .set(KnowledgeImportBatchEntity::getParserMode, parserMode)
                .set(KnowledgeImportBatchEntity::getChunkStrategy, chunkStrategy)
                .set(KnowledgeImportBatchEntity::getChunkSizeTokens, chunkSizeTokens)
                .set(KnowledgeImportBatchEntity::getChunkOverlapTokens, chunkOverlapTokens)
                .set(KnowledgeImportBatchEntity::getEmbeddingProviderId, providerId)
                .set(KnowledgeImportBatchEntity::getEmbeddingModelId, modelId)
                .set(KnowledgeImportBatchEntity::getEmbeddingDimension, dimension)
                .set(KnowledgeImportBatchEntity::getEmbeddingModelFingerprint, modelFingerprint)
                .set(KnowledgeImportBatchEntity::getPreprocessingConfig, preprocessingConfig)
                .set(KnowledgeImportBatchEntity::getConfigHash, configHash)
                .set(KnowledgeImportBatchEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Marks a building batch complete if no active import items remain. */
    public boolean markCompletedIfBuilding(String batchUid, LocalDateTime updatedTime) {
        return lambdaUpdate().eq(KnowledgeImportBatchEntity::getBatchUid, batchUid)
                .eq(KnowledgeImportBatchEntity::getStatus, "BUILDING")
                .set(KnowledgeImportBatchEntity::getStatus, "COMPLETED")
                .set(KnowledgeImportBatchEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
