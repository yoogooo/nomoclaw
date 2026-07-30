package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeRetrievalLogEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeRetrievalLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Repository
public class KnowledgeRetrievalLogRepository extends CrudRepository<KnowledgeRetrievalLogMapper, KnowledgeRetrievalLogEntity> {

    /** Saves one retrieval audit log entry. */
    public void saveLog(String retrievalUid, String conversationUid, String messageUid, String queryText,
                        String knowledgeBaseUids, String embeddingModelFingerprint, int candidateCount,
                        int selectedCount, long latencyMs, String status, String errorMessage,
                        LocalDateTime createdTime) {
        KnowledgeRetrievalLogEntity entity = new KnowledgeRetrievalLogEntity();
        entity.setRetrievalUid(retrievalUid);
        entity.setConversationUid(conversationUid);
        entity.setMessageUid(messageUid);
        entity.setQueryText(queryText);
        entity.setKnowledgeBaseUids(knowledgeBaseUids);
        entity.setEmbeddingModelFingerprint(embeddingModelFingerprint);
        entity.setCandidateCount(candidateCount);
        entity.setSelectedCount(selectedCount);
        entity.setLatencyMs(latencyMs);
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedTime(toDate(createdTime));
        save(entity);
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
