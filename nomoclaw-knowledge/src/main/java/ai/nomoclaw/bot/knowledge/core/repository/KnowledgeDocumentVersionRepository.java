package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentVersionEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentVersionMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
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

    /** Returns the next version number for one document. */
    public int nextVersionNo(String documentUid) {
        return listByDocument(documentUid).stream()
                .map(KnowledgeDocumentVersionEntity::getVersionNo)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    /** Updates one version status. */
    public void updateStatus(String documentVersionUid, String status) {
        lambdaUpdate().eq(KnowledgeDocumentVersionEntity::getDocumentVersionUid, documentVersionUid)
                .set(KnowledgeDocumentVersionEntity::getStatus, status)
                .update();
    }

    /** Updates parse warnings for one document version. */
    public boolean updateParseWarnings(String documentVersionUid, String parseWarnings) {
        return lambdaUpdate().eq(KnowledgeDocumentVersionEntity::getDocumentVersionUid, documentVersionUid)
                .set(KnowledgeDocumentVersionEntity::getParseWarnings, parseWarnings)
                .update();
    }
}
