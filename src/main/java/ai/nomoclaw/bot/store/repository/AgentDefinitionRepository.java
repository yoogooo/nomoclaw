package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.mapper.AgentDefinitionMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class AgentDefinitionRepository extends CrudRepository<AgentDefinitionMapper, AgentDefinitionEntity> {

    public AgentDefinitionEntity findActiveByName(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentDefinitionEntity::getAgentName, agentName)
                .eq(AgentDefinitionEntity::getStatus, "ACTIVE")
                .last("LIMIT 1")
                .one();
    }

    public AgentDefinitionEntity findActiveByUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentDefinitionEntity::getAgentUid, agentUid)
                .eq(AgentDefinitionEntity::getStatus, "ACTIVE")
                .last("LIMIT 1")
                .one();
    }

    public AgentDefinitionEntity findByUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentDefinitionEntity::getAgentUid, agentUid)
                .last("LIMIT 1")
                .one();
    }

    public AgentDefinitionEntity findByName(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentDefinitionEntity::getAgentName, agentName)
                .last("LIMIT 1")
                .one();
    }

    public List<AgentDefinitionEntity> listActiveByUids(Collection<String> agentUids) {
        if (agentUids == null || agentUids.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(AgentDefinitionEntity::getAgentUid, agentUids)
                .eq(AgentDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentDefinitionEntity::getSortIndex, AgentDefinitionEntity::getDisplayName)
                .list();
    }

    public List<AgentDefinitionEntity> listByUids(Collection<String> agentUids) {
        if (agentUids == null || agentUids.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(AgentDefinitionEntity::getAgentUid, agentUids)
                .orderByAsc(AgentDefinitionEntity::getSortIndex, AgentDefinitionEntity::getDisplayName)
                .list();
    }

    public List<AgentDefinitionEntity> listAllActive() {
        return lambdaQuery()
                .eq(AgentDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentDefinitionEntity::getSortIndex, AgentDefinitionEntity::getDisplayName)
                .list();
    }
}
