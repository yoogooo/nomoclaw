package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeChunkEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeChunkMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class KnowledgeChunkRepository extends CrudRepository<KnowledgeChunkMapper, KnowledgeChunkEntity> {

    /** Finds a chunk by its stable business UID. */
    public KnowledgeChunkEntity findByUid(String chunkUid) {
        return lambdaQuery().eq(KnowledgeChunkEntity::getChunkUid, chunkUid).one();
    }

    /** Lists ready chunks in a knowledge base. */
    public List<KnowledgeChunkEntity> listReadyByBase(String knowledgeBaseUid) {
        return lambdaQuery().eq(KnowledgeChunkEntity::getKnowledgeBaseUid, knowledgeBaseUid).eq(KnowledgeChunkEntity::getStatus, "READY").list();
    }

    /** Counts ready chunks in a knowledge base. */
    public long countReadyByBase(String knowledgeBaseUid) {
        return lambdaQuery().eq(KnowledgeChunkEntity::getKnowledgeBaseUid, knowledgeBaseUid).eq(KnowledgeChunkEntity::getStatus, "READY").count();
    }

    /** Lists all ready chunks that belong to the current version of a document. */
    public List<KnowledgeChunkEntity> listReadyByDocumentVersion(String documentUid, String documentVersionUid) {
        return lambdaQuery().eq(KnowledgeChunkEntity::getDocumentUid, documentUid)
                .eq(KnowledgeChunkEntity::getDocumentVersionUid, documentVersionUid)
                .eq(KnowledgeChunkEntity::getStatus, "READY")
                .orderByAsc(KnowledgeChunkEntity::getChunkIndex)
                .list();
    }

    /** Lists chunk business UIDs for one document. */
    public List<String> listChunkUidsByDocument(String documentUid) {
        return lambdaQuery().select(KnowledgeChunkEntity::getChunkUid)
                .eq(KnowledgeChunkEntity::getDocumentUid, documentUid)
                .list()
                .stream()
                .map(KnowledgeChunkEntity::getChunkUid)
                .toList();
    }

    /** Deletes all chunks that belong to one document. */
    public void deleteByDocument(String documentUid) {
        lambdaUpdate().eq(KnowledgeChunkEntity::getDocumentUid, documentUid).remove();
    }

    /** Deletes all chunks for a document version. */
    public void deleteByDocumentVersion(String documentVersionUid) {
        lambdaUpdate().eq(KnowledgeChunkEntity::getDocumentVersionUid, documentVersionUid).remove();
    }

    /** Deletes staged or failed chunks for a document version while retaining ready chunks. */
    public void deleteNonReadyByDocumentVersion(String documentVersionUid) {
        lambdaUpdate().eq(KnowledgeChunkEntity::getDocumentVersionUid, documentVersionUid)
                .ne(KnowledgeChunkEntity::getStatus, "READY")
                .remove();
    }

    /** Marks staged chunks for one version ready. */
    public void markStagedReady(String documentVersionUid) {
        lambdaUpdate().eq(KnowledgeChunkEntity::getDocumentVersionUid, documentVersionUid)
                .eq(KnowledgeChunkEntity::getStatus, "STAGED")
                .set(KnowledgeChunkEntity::getStatus, "READY")
                .update();
    }

    /** Updates a staged chunk by business UID. */
    public boolean updateStagedByUid(KnowledgeChunkEntity chunk) {
        return lambdaUpdate().eq(KnowledgeChunkEntity::getChunkUid, chunk.getChunkUid())
                .set(KnowledgeChunkEntity::getContent, chunk.getContent())
                .set(KnowledgeChunkEntity::getTokenCount, chunk.getTokenCount())
                .set(KnowledgeChunkEntity::getContentHash, chunk.getContentHash())
                .set(KnowledgeChunkEntity::getPageFrom, chunk.getPageFrom())
                .set(KnowledgeChunkEntity::getPageTo, chunk.getPageTo())
                .set(KnowledgeChunkEntity::getSectionPath, chunk.getSectionPath())
                .set(KnowledgeChunkEntity::getCharStart, chunk.getCharStart())
                .set(KnowledgeChunkEntity::getCharEnd, chunk.getCharEnd())
                .set(KnowledgeChunkEntity::getVectorPointId, chunk.getVectorPointId())
                .set(KnowledgeChunkEntity::getStatus, "STAGED")
                .update();
    }

    /** Lists ready chunks whose version is currently published on their document. */
    public List<KnowledgeChunkEntity> listReadyCurrentVersionByBase(String knowledgeBaseUid,
                                                                    Collection<String> currentVersionUids) {
        if (currentVersionUids == null || currentVersionUids.isEmpty()) return List.of();
        return lambdaQuery().eq(KnowledgeChunkEntity::getKnowledgeBaseUid, knowledgeBaseUid)
                .eq(KnowledgeChunkEntity::getStatus, "READY")
                .in(KnowledgeChunkEntity::getDocumentVersionUid, currentVersionUids)
                .list();
    }
}
