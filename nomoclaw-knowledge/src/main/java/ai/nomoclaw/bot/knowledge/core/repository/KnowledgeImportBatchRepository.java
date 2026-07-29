package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeImportBatchEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportBatchMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

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
}
