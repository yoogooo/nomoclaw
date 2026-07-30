package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.MessageKnowledgeCitationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.MessageKnowledgeCitationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/** Repository for persisted knowledge citations. */
@Repository
public class MessageKnowledgeCitationRepository extends CrudRepository<MessageKnowledgeCitationMapper, MessageKnowledgeCitationEntity> {

    /** Deletes citations that point to any of the supplied chunks. */
    public void deleteByChunkUids(Collection<String> chunkUids) {
        if (chunkUids == null || chunkUids.isEmpty()) {
            return;
        }
        remove(Wrappers.<MessageKnowledgeCitationEntity>lambdaQuery()
                .in(MessageKnowledgeCitationEntity::getChunkUid, chunkUids));
    }

    /** Lists citations for a message in rank order. */
    public List<MessageKnowledgeCitationEntity> listByMessage(String messageUid) {
        return lambdaQuery().eq(MessageKnowledgeCitationEntity::getMessageUid, messageUid)
                .orderByAsc(MessageKnowledgeCitationEntity::getRankIndex)
                .list();
    }

    /** Saves one message citation. */
    public void saveCitation(String messageUid, String assistantMessageUid, String retrievalUid, String chunkUid,
                             int rankIndex, double score, LocalDateTime createdTime) {
        MessageKnowledgeCitationEntity entity = new MessageKnowledgeCitationEntity();
        entity.setMessageUid(messageUid);
        entity.setAssistantMessageUid(assistantMessageUid);
        entity.setRetrievalUid(retrievalUid);
        entity.setChunkUid(chunkUid);
        entity.setRankIndex(rankIndex);
        entity.setScore(score);
        entity.setCreatedTime(toDate(createdTime));
        save(entity);
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
