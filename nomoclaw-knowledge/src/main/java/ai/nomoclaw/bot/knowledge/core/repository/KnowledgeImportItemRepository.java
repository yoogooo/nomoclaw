package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeImportItemEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportItemMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
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

    /** Marks one item as building against the generated document version. */
    public void markBuilding(String itemUid, String versionUid, LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeImportItemEntity::getItemUid, itemUid)
                .set(KnowledgeImportItemEntity::getDocumentVersionUid, versionUid)
                .set(KnowledgeImportItemEntity::getStatus, "BUILDING")
                .set(KnowledgeImportItemEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Updates all import items for a version with the final status. */
    public void finalizeByDocumentVersion(String versionUid, String status, String errorCode, String errorMessage,
                                          LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeImportItemEntity::getDocumentVersionUid, versionUid)
                .set(KnowledgeImportItemEntity::getStatus, status)
                .set(KnowledgeImportItemEntity::getErrorCode, errorCode)
                .set(KnowledgeImportItemEntity::getErrorMessage, errorMessage)
                .set(KnowledgeImportItemEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
