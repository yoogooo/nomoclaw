package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.AgentConversationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeAgentConversationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Repository for looking up conversation ownership from knowledge module. */
@Repository
public class KnowledgeAgentConversationRepository extends CrudRepository<KnowledgeAgentConversationMapper, AgentConversationEntity> {

    /** Finds one conversation by its stable business UID. */
    public AgentConversationEntity findByConversationUid(String conversationUid) {
        return lambdaQuery().eq(AgentConversationEntity::getConversationUid, conversationUid).one();
    }
}
