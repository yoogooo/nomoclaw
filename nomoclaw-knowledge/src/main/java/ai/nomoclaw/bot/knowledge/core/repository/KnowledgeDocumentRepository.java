package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
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

    /** Marks a document as processing and clears previous failures. */
    public void markProcessing(String documentUid, LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeDocumentEntity::getDocumentUid, documentUid)
                .set(KnowledgeDocumentEntity::getStatus, "PROCESSING")
                .set(KnowledgeDocumentEntity::getFailureCode, "")
                .set(KnowledgeDocumentEntity::getFailureMessage, "")
                .set(KnowledgeDocumentEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Updates the document status while retaining its current version. */
    public void updateStatus(String documentUid, String status, LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeDocumentEntity::getDocumentUid, documentUid)
                .set(KnowledgeDocumentEntity::getStatus, status)
                .set(KnowledgeDocumentEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Clears failure metadata and updates the document status. */
    public void clearFailureAndSetStatus(String documentUid, String status, LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeDocumentEntity::getDocumentUid, documentUid)
                .set(KnowledgeDocumentEntity::getStatus, status)
                .set(KnowledgeDocumentEntity::getFailureCode, "")
                .set(KnowledgeDocumentEntity::getFailureMessage, "")
                .set(KnowledgeDocumentEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Publishes the completed version as the current searchable document version. */
    public void publishVersion(String documentUid, String versionUid, int pageCount, int chunkCount,
                               LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeDocumentEntity::getDocumentUid, documentUid)
                .set(KnowledgeDocumentEntity::getCurrentVersionUid, versionUid)
                .set(KnowledgeDocumentEntity::getStatus, "READY")
                .set(KnowledgeDocumentEntity::getFailureCode, "")
                .set(KnowledgeDocumentEntity::getFailureMessage, "")
                .set(KnowledgeDocumentEntity::getPageCount, pageCount)
                .set(KnowledgeDocumentEntity::getChunkCount, chunkCount)
                .set(KnowledgeDocumentEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Records a build failure status on a document. */
    public void updateFailureState(String documentUid, String status, String failureCode, String failureMessage,
                                   LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeDocumentEntity::getDocumentUid, documentUid)
                .set(KnowledgeDocumentEntity::getStatus, status)
                .set(KnowledgeDocumentEntity::getFailureCode, failureCode)
                .set(KnowledgeDocumentEntity::getFailureMessage, failureMessage)
                .set(KnowledgeDocumentEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
