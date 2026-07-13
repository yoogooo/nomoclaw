package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.AgentTipEntity;
import ai.nomoclaw.bot.store.mapper.AgentTipMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentTipRepository extends CrudRepository<AgentTipMapper, AgentTipEntity> {

    public List<AgentTipEntity> listActiveByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentTipEntity::getAgentUid, agentUid)
                .eq(AgentTipEntity::getStatus, "ACTIVE")
                .orderByDesc(AgentTipEntity::getUpdatedTime)
                .orderByAsc(AgentTipEntity::getSortIndex, AgentTipEntity::getId)
                .list();
    }

    public AgentTipEntity findByAgentUidAndTipUid(String agentUid, String tipUid) {
        if (agentUid == null || agentUid.isBlank() || tipUid == null || tipUid.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentTipEntity::getAgentUid, agentUid)
                .eq(AgentTipEntity::getTipUid, tipUid)
                .last("LIMIT 1")
                .one();
    }

    public AgentTipEntity findByAgentUidAndSourceMessageUid(String agentUid, String sourceMessageUid) {
        if (agentUid == null || agentUid.isBlank() || sourceMessageUid == null || sourceMessageUid.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentTipEntity::getAgentUid, agentUid)
                .eq(AgentTipEntity::getSourceMessageUid, sourceMessageUid)
                .last("LIMIT 1")
                .one();
    }

    public void deleteByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return;
        }
        lambdaUpdate()
                .eq(AgentTipEntity::getAgentUid, agentUid)
                .remove();
    }
}
