package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.AgentKnowledgeBaseRelationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.AgentKnowledgeBaseRelationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
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

    /** Saves one enabled agent binding. */
    public void saveEnabledRelation(String agentUid, String knowledgeBaseUid, LocalDateTime now) {
        AgentKnowledgeBaseRelationEntity entity = new AgentKnowledgeBaseRelationEntity();
        entity.setAgentUid(agentUid);
        entity.setKnowledgeBaseUid(knowledgeBaseUid);
        entity.setEnabled(true);
        entity.setCreatedTime(toDate(now));
        entity.setUpdatedTime(toDate(now));
        save(entity);
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
