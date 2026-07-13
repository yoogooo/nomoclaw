package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.AgentKnowledgeBaseRelationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.AgentKnowledgeBaseRelationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Repository for Agent knowledge-base bindings. */
@Repository
public class AgentKnowledgeBaseRelationRepository extends CrudRepository<AgentKnowledgeBaseRelationMapper, AgentKnowledgeBaseRelationEntity> { }
