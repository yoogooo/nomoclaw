package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentEventEntity;
import ai.nomoclaw.bot.store.mapper.AgentEventMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentEventRepository extends CrudRepository<AgentEventMapper, AgentEventEntity> {

    public List<AgentEventEntity> listByConversationUid(String conversationUid) {
        if (conversationUid == null || conversationUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentEventEntity::getConversationUid, conversationUid)
                .orderByAsc(AgentEventEntity::getCreatedTime, AgentEventEntity::getId)
                .list();
    }

    public List<AgentEventEntity> listByMessageUid(String messageUid) {
        return lambdaQuery()
                .eq(AgentEventEntity::getMessageUid, messageUid)
                .orderByAsc(AgentEventEntity::getCreatedTime)
                .list();
    }

    public void deleteByConversationUid(String conversationUid) {
        lambdaUpdate()
                .eq(AgentEventEntity::getConversationUid, conversationUid)
                .remove();
    }
}
