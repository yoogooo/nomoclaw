package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.ConversationKnowledgeBaseRelationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.ConversationKnowledgeBaseRelationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

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
}
