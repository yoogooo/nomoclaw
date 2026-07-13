package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentStepEntity;
import ai.nomoclaw.bot.store.mapper.AgentStepMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentStepRepository extends CrudRepository<AgentStepMapper, AgentStepEntity> {

    public List<AgentStepEntity> listByMessageId(String messageUid) {
        return lambdaQuery()
                .eq(AgentStepEntity::getMessageUid, messageUid)
                .orderByAsc(AgentStepEntity::getRoundIndex, AgentStepEntity::getStepIndex)
                .list();
    }

    public List<AgentStepEntity> listByMessageIdAndRoundIndex(String messageUid, int roundIndex) {
        return lambdaQuery()
                .eq(AgentStepEntity::getMessageUid, messageUid)
                .eq(AgentStepEntity::getRoundIndex, roundIndex)
                .orderByAsc(AgentStepEntity::getStepIndex)
                .list();
    }

    public AgentStepEntity findByStepId(String stepUid) {
        return lambdaQuery()
                .eq(AgentStepEntity::getStepUid, stepUid)
                .last("LIMIT 1")
                .one();
    }

    public void deleteByConversationUid(String conversationUid) {
        lambdaUpdate()
                .eq(AgentStepEntity::getConversationUid, conversationUid)
                .remove();
    }
}
