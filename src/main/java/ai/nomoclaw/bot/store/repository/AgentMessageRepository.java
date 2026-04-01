package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentMessageEntity;
import ai.nomoclaw.bot.store.mapper.AgentMessageMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentMessageRepository extends CrudRepository<AgentMessageMapper, AgentMessageEntity> {

    public AgentMessageEntity findByMessageId(String messageUid) {
        return lambdaQuery()
                .eq(AgentMessageEntity::getMessageUid, messageUid)
                .last("LIMIT 1")
                .one();
    }

    public AgentMessageEntity findLatestUserMessageByConversation(String conversationUid) {
        return lambdaQuery()
                .eq(AgentMessageEntity::getConversationUid, conversationUid)
                .eq(AgentMessageEntity::getRole, "user")
                .orderByDesc(AgentMessageEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public List<AgentMessageEntity> listByConversation(String conversationUid) {
        return lambdaQuery()
                .eq(AgentMessageEntity::getConversationUid, conversationUid)
                .orderByAsc(AgentMessageEntity::getId)
                .list();
    }

    public void deleteByConversationUid(String conversationUid) {
        lambdaUpdate()
                .eq(AgentMessageEntity::getConversationUid, conversationUid)
                .remove();
    }
}
