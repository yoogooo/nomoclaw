package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.AgentCommandExecutionEntity;
import ai.nomoclaw.bot.store.mapper.AgentCommandExecutionMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentCommandExecutionRepository extends CrudRepository<AgentCommandExecutionMapper, AgentCommandExecutionEntity> {

    public List<AgentCommandExecutionEntity> listByMessageUid(String messageUid) {
        return lambdaQuery()
                .eq(AgentCommandExecutionEntity::getMessageUid, messageUid)
                .orderByAsc(AgentCommandExecutionEntity::getCreatedTime, AgentCommandExecutionEntity::getAttempt)
                .list();
    }

    public void deleteByConversationUid(String conversationUid) {
        lambdaUpdate()
                .eq(AgentCommandExecutionEntity::getConversationUid, conversationUid)
                .remove();
    }
}
