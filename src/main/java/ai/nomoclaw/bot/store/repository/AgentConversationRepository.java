package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentConversationEntity;
import ai.nomoclaw.bot.store.mapper.AgentConversationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentConversationRepository extends CrudRepository<AgentConversationMapper, AgentConversationEntity> {

    public AgentConversationEntity findByConversationUid(String conversationUid) {
        return lambdaQuery()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .last("LIMIT 1")
                .one();
    }

    public List<AgentConversationEntity> listAllDesc() {
        return lambdaQuery()
                .orderByDesc(AgentConversationEntity::getPinned, AgentConversationEntity::getUpdatedTime, AgentConversationEntity::getId)
                .list();
    }


    public List<AgentConversationEntity> listAllDescExcludeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return listAllDesc();
        }
        return lambdaQuery()
                .ne(AgentConversationEntity::getChannel, channel)
                .orderByDesc(AgentConversationEntity::getPinned, AgentConversationEntity::getUpdatedTime, AgentConversationEntity::getId)
                .list();
    }

    public void deleteByConversationUid(String conversationUid) {
        lambdaUpdate()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .remove();
    }

    public List<AgentConversationEntity> listByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentConversationEntity::getAgentUid, agentUid)
                .orderByDesc(AgentConversationEntity::getPinned, AgentConversationEntity::getUpdatedTime, AgentConversationEntity::getId)
                .list();
    }
}
