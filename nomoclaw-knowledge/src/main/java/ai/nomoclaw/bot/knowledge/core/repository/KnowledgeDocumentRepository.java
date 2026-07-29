package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class KnowledgeDocumentRepository extends CrudRepository<KnowledgeDocumentMapper, KnowledgeDocumentEntity> {

    /** Finds one document within its knowledge base. */
    public KnowledgeDocumentEntity findByBaseAndUid(String knowledgeBaseUid, String documentUid) {
        return lambdaQuery().eq(KnowledgeDocumentEntity::getKnowledgeBaseUid, knowledgeBaseUid).eq(KnowledgeDocumentEntity::getDocumentUid, documentUid).one();
    }

    /** Finds a duplicate upload by checksum. */
    public KnowledgeDocumentEntity findByChecksum(String knowledgeBaseUid, String checksumSha256) {
        return lambdaQuery().eq(KnowledgeDocumentEntity::getKnowledgeBaseUid, knowledgeBaseUid).eq(KnowledgeDocumentEntity::getChecksumSha256, checksumSha256).one();
    }

    /** Lists documents for a knowledge base from newest to oldest. */
    public List<KnowledgeDocumentEntity> listByBase(String knowledgeBaseUid) {
        return lambdaQuery().eq(KnowledgeDocumentEntity::getKnowledgeBaseUid, knowledgeBaseUid).orderByDesc(KnowledgeDocumentEntity::getId).list();
    }

    /** Finds one document by its stable business UID. */
    public KnowledgeDocumentEntity findByUid(String documentUid) {
        return lambdaQuery().eq(KnowledgeDocumentEntity::getDocumentUid, documentUid).one();
    }

    /** Lists documents for a batch of business UIDs. */
    public List<KnowledgeDocumentEntity> listByUids(Collection<String> documentUids) {
        if (documentUids == null || documentUids.isEmpty()) return List.of();
        return lambdaQuery().in(KnowledgeDocumentEntity::getDocumentUid, documentUids).list();
    }

    /** Counts ready documents in a knowledge base. */
    public long countReadyByBase(String knowledgeBaseUid) {
        return lambdaQuery().eq(KnowledgeDocumentEntity::getKnowledgeBaseUid, knowledgeBaseUid).eq(KnowledgeDocumentEntity::getStatus, "READY").count();
    }

    /** Deletes one document by its stable business UID. */
    public void deleteByUid(String documentUid) {
        lambdaUpdate().eq(KnowledgeDocumentEntity::getDocumentUid, documentUid).remove();
    }

}
