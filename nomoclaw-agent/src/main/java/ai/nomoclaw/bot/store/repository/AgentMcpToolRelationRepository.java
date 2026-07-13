package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.AgentMcpToolRelationEntity;
import ai.nomoclaw.bot.store.mapper.AgentMcpToolRelationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentMcpToolRelationRepository extends CrudRepository<AgentMcpToolRelationMapper, AgentMcpToolRelationEntity> {

    public List<AgentMcpToolRelationEntity> listByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentMcpToolRelationEntity::getAgentUid, agentUid)
                .orderByAsc(AgentMcpToolRelationEntity::getSortIndex, AgentMcpToolRelationEntity::getId)
                .list();
    }

    public List<AgentMcpToolRelationEntity> listActiveByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentMcpToolRelationEntity::getAgentUid, agentUid)
                .eq(AgentMcpToolRelationEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentMcpToolRelationEntity::getSortIndex, AgentMcpToolRelationEntity::getId)
                .list();
    }

    public AgentMcpToolRelationEntity findByAgentUidAndToolKey(String agentUid, String toolKey) {
        if (agentUid == null || agentUid.isBlank() || toolKey == null || toolKey.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentMcpToolRelationEntity::getAgentUid, agentUid)
                .eq(AgentMcpToolRelationEntity::getToolKey, toolKey)
                .one();
    }

    public void deleteByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return;
        }
        lambdaUpdate()
                .eq(AgentMcpToolRelationEntity::getAgentUid, agentUid)
                .remove();
    }

    public void deleteByToolKey(String toolKey) {
        if (toolKey == null || toolKey.isBlank()) {
            return;
        }
        lambdaUpdate()
                .eq(AgentMcpToolRelationEntity::getToolKey, toolKey)
                .remove();
    }

    public void updateToolKey(String oldToolKey, String newToolKey) {
        if (oldToolKey == null || oldToolKey.isBlank() || newToolKey == null || newToolKey.isBlank()
                || oldToolKey.equals(newToolKey)) {
            return;
        }
        lambdaUpdate()
                .eq(AgentMcpToolRelationEntity::getToolKey, oldToolKey)
                .set(AgentMcpToolRelationEntity::getToolKey, newToolKey)
                .update();
    }
}
