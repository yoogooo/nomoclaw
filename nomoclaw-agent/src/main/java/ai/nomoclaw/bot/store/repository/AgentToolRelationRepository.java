package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentToolRelationEntity;
import ai.nomoclaw.bot.store.mapper.AgentToolRelationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentToolRelationRepository extends CrudRepository<AgentToolRelationMapper, AgentToolRelationEntity> {

    public List<AgentToolRelationEntity> listByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentToolRelationEntity::getAgentUid, agentUid)
                .orderByAsc(AgentToolRelationEntity::getSortIndex, AgentToolRelationEntity::getId)
                .list();
    }

    public List<AgentToolRelationEntity> listActiveByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentToolRelationEntity::getAgentUid, agentUid)
                .eq(AgentToolRelationEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentToolRelationEntity::getSortIndex, AgentToolRelationEntity::getId)
                .list();
    }

    public AgentToolRelationEntity findByAgentUidAndToolKey(String agentUid, String toolKey) {
        if (agentUid == null || agentUid.isBlank() || toolKey == null || toolKey.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentToolRelationEntity::getAgentUid, agentUid)
                .eq(AgentToolRelationEntity::getToolKey, toolKey)
                .one();
    }

    public void deleteByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return;
        }
        lambdaUpdate()
                .eq(AgentToolRelationEntity::getAgentUid, agentUid)
                .remove();
    }

    public void deleteByToolKey(String toolKey) {
        if (toolKey == null || toolKey.isBlank()) {
            return;
        }
        lambdaUpdate()
                .eq(AgentToolRelationEntity::getToolKey, toolKey)
                .remove();
    }
}
