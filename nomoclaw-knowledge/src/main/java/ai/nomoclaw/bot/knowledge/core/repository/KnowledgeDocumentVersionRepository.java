package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentVersionEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentVersionMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KnowledgeDocumentVersionRepository extends CrudRepository<KnowledgeDocumentVersionMapper, KnowledgeDocumentVersionEntity> {

    /** Finds one document version by business UID. */
    public KnowledgeDocumentVersionEntity findByUid(String documentVersionUid) {
        return lambdaQuery().eq(KnowledgeDocumentVersionEntity::getDocumentVersionUid, documentVersionUid).one();
    }

    /** Lists document versions for one document from newest to oldest. */
    public List<KnowledgeDocumentVersionEntity> listByDocument(String documentUid) {
        return lambdaQuery().eq(KnowledgeDocumentVersionEntity::getDocumentUid, documentUid)
                .orderByDesc(KnowledgeDocumentVersionEntity::getId).list();
    }

    /** Lists document versions by their business UIDs. */
    public List<KnowledgeDocumentVersionEntity> listByUids(List<String> versionUids) {
        if (versionUids == null || versionUids.isEmpty()) return List.of();
        return lambdaQuery().in(KnowledgeDocumentVersionEntity::getDocumentVersionUid, versionUids).list();
    }

    /** Deletes all immutable versions that belong to one document. */
    public void deleteByDocument(String documentUid) {
        lambdaUpdate().eq(KnowledgeDocumentVersionEntity::getDocumentUid, documentUid).remove();
    }
}
