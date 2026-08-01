package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentNodeEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentNodeMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for document structure nodes.
 */
@Repository
public class KnowledgeDocumentNodeRepository
        extends CrudRepository<KnowledgeDocumentNodeMapper, KnowledgeDocumentNodeEntity> {

    public List<KnowledgeDocumentNodeEntity> listByVersion(String documentVersionUid) {
        return lambdaQuery().eq(KnowledgeDocumentNodeEntity::getDocumentVersionUid, documentVersionUid)
                .orderByAsc(KnowledgeDocumentNodeEntity::getId).list();
    }

    public KnowledgeDocumentNodeEntity findByUid(String nodeUid) {
        if (nodeUid == null || nodeUid.isBlank()) return null;
        return lambdaQuery().eq(KnowledgeDocumentNodeEntity::getNodeUid, nodeUid).one();
    }

    public void deleteByVersion(String documentVersionUid) {
        lambdaUpdate().eq(KnowledgeDocumentNodeEntity::getDocumentVersionUid, documentVersionUid).remove();
    }

    public void deleteByDocument(String documentUid) {
        lambdaUpdate().eq(KnowledgeDocumentNodeEntity::getDocumentUid, documentUid).remove();
    }
}
