package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeBaseEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeBaseMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Collection;
import java.util.List;

@Repository
public class KnowledgeBaseRepository extends CrudRepository<KnowledgeBaseMapper, KnowledgeBaseEntity> {

    /** Finds a knowledge base by its stable business UID. */
    public KnowledgeBaseEntity findByUid(String knowledgeBaseUid) {
        return lambdaQuery().eq(KnowledgeBaseEntity::getKnowledgeBaseUid, knowledgeBaseUid).one();
    }

    /** Lists visible knowledge bases matching an optional name keyword. */
    public List<KnowledgeBaseEntity> listVisible(String keyword) {
        return lambdaQuery().like(keyword != null && !keyword.isBlank(), KnowledgeBaseEntity::getName, keyword)
                .ne(KnowledgeBaseEntity::getStatus, "DELETING").orderByDesc(KnowledgeBaseEntity::getUpdatedTime).list();
    }

    /** Lists bases that still need a dedicated Qdrant collection name. */
    public List<KnowledgeBaseEntity> listLegacyCollections() {
        return lambdaQuery().eq(KnowledgeBaseEntity::getVectorCollectionName, "").list();
    }

    /** Returns bases for the supplied business UIDs. */
    public List<KnowledgeBaseEntity> listByUids(Collection<String> knowledgeBaseUids) {
        if (knowledgeBaseUids == null || knowledgeBaseUids.isEmpty()) return List.of();
        return lambdaQuery().in(KnowledgeBaseEntity::getKnowledgeBaseUid, knowledgeBaseUids).list();
    }

    /** Updates editable retrieval metadata for one knowledge base. */
    public void updateEditableFields(String knowledgeBaseUid, String name, String description, int topK,
                                     double threshold, LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeBaseEntity::getKnowledgeBaseUid, knowledgeBaseUid)
                .set(KnowledgeBaseEntity::getName, name)
                .set(KnowledgeBaseEntity::getDescription, description)
                .set(KnowledgeBaseEntity::getRetrievalTopK, topK)
                .set(KnowledgeBaseEntity::getSimilarityThreshold, threshold)
                .set(KnowledgeBaseEntity::getUpdatedTime, Date.from(updatedTime.atZone(ZoneId.systemDefault()).toInstant()))
                .update();
    }

    /** Refreshes ready document and chunk counters for one knowledge base. */
    public void updateCounts(String knowledgeBaseUid, long documentCount, long chunkCount, LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeBaseEntity::getKnowledgeBaseUid, knowledgeBaseUid)
                .set(KnowledgeBaseEntity::getDocumentCount, Math.toIntExact(documentCount))
                .set(KnowledgeBaseEntity::getChunkCount, chunkCount)
                .set(KnowledgeBaseEntity::getUpdatedTime, Date.from(updatedTime.atZone(ZoneId.systemDefault()).toInstant()))
                .update();
    }

    /** Sets the dedicated vector collection for a migrated base only when still unset. */
    public boolean assignLegacyCollection(String knowledgeBaseUid, String collectionName, LocalDateTime updatedTime) {
        return lambdaUpdate()
                .eq(KnowledgeBaseEntity::getKnowledgeBaseUid, knowledgeBaseUid)
                .eq(KnowledgeBaseEntity::getVectorCollectionName, "")
                .set(KnowledgeBaseEntity::getVectorCollectionName, collectionName)
                .set(KnowledgeBaseEntity::getUpdatedTime, Date.from(updatedTime.atZone(ZoneId.systemDefault()).toInstant()))
                .update();
    }
}
