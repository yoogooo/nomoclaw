package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.ConversationKnowledgeBaseRelationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.ConversationKnowledgeBaseRelationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/** Repository for conversation knowledge-base overrides. */
@Repository
public class ConversationKnowledgeBaseRelationRepository extends CrudRepository<ConversationKnowledgeBaseRelationMapper, ConversationKnowledgeBaseRelationEntity> {

    /** Lists bound knowledge-base business UIDs for a conversation and mode. */
    public List<String> listKnowledgeBaseUids(String conversationUid, String mode) {
        return lambdaQuery().select(ConversationKnowledgeBaseRelationEntity::getKnowledgeBaseUid)
                .eq(ConversationKnowledgeBaseRelationEntity::getConversationUid, conversationUid)
                .eq(ConversationKnowledgeBaseRelationEntity::getMode, mode)
                .list()
                .stream()
                .map(ConversationKnowledgeBaseRelationEntity::getKnowledgeBaseUid)
                .toList();
    }

    /** Removes all overrides for one conversation. */
    public void deleteByConversation(String conversationUid) {
        lambdaUpdate().eq(ConversationKnowledgeBaseRelationEntity::getConversationUid, conversationUid).remove();
    }

    /** Saves one conversation-level relation. */
    public void saveRelation(String conversationUid, String knowledgeBaseUid, String mode, LocalDateTime now) {
        ConversationKnowledgeBaseRelationEntity entity = new ConversationKnowledgeBaseRelationEntity();
        entity.setConversationUid(conversationUid);
        entity.setKnowledgeBaseUid(knowledgeBaseUid);
        entity.setMode(mode);
        entity.setCreatedTime(toDate(now));
        entity.setUpdatedTime(toDate(now));
        save(entity);
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
