package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.MessageKnowledgeCitationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.MessageKnowledgeCitationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
}
