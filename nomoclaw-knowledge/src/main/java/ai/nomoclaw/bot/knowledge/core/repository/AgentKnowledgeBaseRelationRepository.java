package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.AgentKnowledgeBaseRelationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.AgentKnowledgeBaseRelationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Repository for Agent knowledge-base bindings. */
@Repository
public class AgentKnowledgeBaseRelationRepository extends CrudRepository<AgentKnowledgeBaseRelationMapper, AgentKnowledgeBaseRelationEntity> {

    /** Lists enabled knowledge-base business UIDs for one agent. */
    public List<String> listEnabledKnowledgeBaseUids(String agentUid) {
        return lambdaQuery().select(AgentKnowledgeBaseRelationEntity::getKnowledgeBaseUid)
                .eq(AgentKnowledgeBaseRelationEntity::getAgentUid, agentUid)
                .eq(AgentKnowledgeBaseRelationEntity::getEnabled, true)
                .list()
                .stream()
                .map(AgentKnowledgeBaseRelationEntity::getKnowledgeBaseUid)
                .toList();
    }

    /** Removes all bindings for one agent. */
    public void deleteByAgent(String agentUid) {
        lambdaUpdate().eq(AgentKnowledgeBaseRelationEntity::getAgentUid, agentUid).remove();
    }
}
