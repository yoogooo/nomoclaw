package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentGroupDefinitionEntity;
import ai.nomoclaw.bot.store.mapper.AgentGroupDefinitionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentGroupDefinitionRepository extends CrudRepository<AgentGroupDefinitionMapper, AgentGroupDefinitionEntity> {

    public List<AgentGroupDefinitionEntity> listActive() {
        return lambdaQuery()
                .eq(AgentGroupDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentGroupDefinitionEntity::getSortIndex, AgentGroupDefinitionEntity::getDisplayName)
                .list();
    }

    public AgentGroupDefinitionEntity findActiveByUid(String agentGroupUid) {
        if (agentGroupUid == null || agentGroupUid.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentGroupDefinitionEntity::getAgentGroupUid, agentGroupUid)
                .eq(AgentGroupDefinitionEntity::getStatus, "ACTIVE")
                .last("LIMIT 1")
                .one();
    }

    public AgentGroupDefinitionEntity findByName(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentGroupDefinitionEntity::getGroupName, groupName)
                .last("LIMIT 1")
                .one();
    }
}
