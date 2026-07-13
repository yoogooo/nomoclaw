package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Repository;

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

    /** Counts ready documents in a knowledge base. */
    public long countReadyByBase(String knowledgeBaseUid) {
        return lambdaQuery().eq(KnowledgeDocumentEntity::getKnowledgeBaseUid, knowledgeBaseUid).eq(KnowledgeDocumentEntity::getStatus, "READY").count();
    }

}
