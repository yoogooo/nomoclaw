package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentSkillRelationEntity;
import ai.nomoclaw.bot.store.mapper.AgentSkillRelationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentSkillRelationRepository extends CrudRepository<AgentSkillRelationMapper, AgentSkillRelationEntity> {

    public List<AgentSkillRelationEntity> listByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentSkillRelationEntity::getAgentUid, agentUid)
                .orderByAsc(AgentSkillRelationEntity::getSortIndex, AgentSkillRelationEntity::getId)
                .list();
    }

    public List<AgentSkillRelationEntity> listActiveByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentSkillRelationEntity::getAgentUid, agentUid)
                .eq(AgentSkillRelationEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentSkillRelationEntity::getSortIndex, AgentSkillRelationEntity::getId)
                .list();
    }

    public AgentSkillRelationEntity findByAgentUidAndSkillKey(String agentUid, String skillKey) {
        if (agentUid == null || agentUid.isBlank() || skillKey == null || skillKey.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentSkillRelationEntity::getAgentUid, agentUid)
                .eq(AgentSkillRelationEntity::getSkillKey, skillKey)
                .one();
    }
}
