package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.ConversationKnowledgeBaseRelationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.ConversationKnowledgeBaseRelationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Repository for conversation knowledge-base overrides. */
@Repository
public class ConversationKnowledgeBaseRelationRepository extends CrudRepository<ConversationKnowledgeBaseRelationMapper, ConversationKnowledgeBaseRelationEntity> { }
