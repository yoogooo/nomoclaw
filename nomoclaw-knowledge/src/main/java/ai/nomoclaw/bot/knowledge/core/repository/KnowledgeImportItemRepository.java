package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeImportItemEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportItemMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for knowledge import items.
 */
@Repository
public class KnowledgeImportItemRepository extends CrudRepository<KnowledgeImportItemMapper, KnowledgeImportItemEntity> {

    /** Lists import items in creation order. */
    public List<KnowledgeImportItemEntity> listByBatch(String batchUid) {
        return lambdaQuery().eq(KnowledgeImportItemEntity::getBatchUid, batchUid)
                .orderByAsc(KnowledgeImportItemEntity::getId).list();
    }

    /** Lists accepted import items in creation order. */
    public List<KnowledgeImportItemEntity> listAcceptedByBatch(String batchUid) {
        return lambdaQuery().eq(KnowledgeImportItemEntity::getBatchUid, batchUid)
                .eq(KnowledgeImportItemEntity::getOutcome, "ACCEPTED")
                .orderByAsc(KnowledgeImportItemEntity::getId)
                .list();
    }

    /** Counts accepted import items in one batch. */
    public long countAcceptedByBatch(String batchUid) {
        return lambdaQuery().eq(KnowledgeImportItemEntity::getBatchUid, batchUid)
                .eq(KnowledgeImportItemEntity::getOutcome, "ACCEPTED")
                .count();
    }

    /** Lists import items for one document version. */
    public List<KnowledgeImportItemEntity> listByDocumentVersion(String versionUid) {
        return lambdaQuery().eq(KnowledgeImportItemEntity::getDocumentVersionUid, versionUid).list();
    }

    /** Lists all import items for one document. */
    public List<KnowledgeImportItemEntity> listByDocument(String documentUid) {
        return lambdaQuery().eq(KnowledgeImportItemEntity::getDocumentUid, documentUid).list();
    }

    /** Lists batch business UIDs for one document version. */
    public List<String> listBatchUidsByDocumentVersion(String versionUid) {
        return listByDocumentVersion(versionUid).stream().map(KnowledgeImportItemEntity::getBatchUid).distinct().toList();
    }

    /** Counts active items in a batch that still keep the batch open. */
    public long countActiveByBatch(String batchUid) {
        return lambdaQuery().eq(KnowledgeImportItemEntity::getBatchUid, batchUid)
                .in(KnowledgeImportItemEntity::getStatus, List.of("UPLOADED", "BUILDING"))
                .count();
    }

    /** Returns the newest draft import batch UID for a document, if any. */
    public String findLatestBatchUidByDocument(Collection<String> draftBatchUids, String documentUid) {
        if (draftBatchUids == null || draftBatchUids.isEmpty() || documentUid == null || documentUid.isBlank()) return null;
        List<KnowledgeImportItemEntity> items = lambdaQuery().in(KnowledgeImportItemEntity::getBatchUid, draftBatchUids)
                .eq(KnowledgeImportItemEntity::getDocumentUid, documentUid)
                .orderByDesc(KnowledgeImportItemEntity::getId)
                .list();
        return items.isEmpty() ? null : items.get(0).getBatchUid();
    }

    /** Deletes all import items that belong to one document. */
    public void deleteByDocument(String documentUid) {
        lambdaUpdate().eq(KnowledgeImportItemEntity::getDocumentUid, documentUid).remove();
    }
}
