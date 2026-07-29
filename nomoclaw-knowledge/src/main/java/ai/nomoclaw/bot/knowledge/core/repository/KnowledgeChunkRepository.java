package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeChunkEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeChunkMapper;
import org.springframework.stereotype.Repository;

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

}
