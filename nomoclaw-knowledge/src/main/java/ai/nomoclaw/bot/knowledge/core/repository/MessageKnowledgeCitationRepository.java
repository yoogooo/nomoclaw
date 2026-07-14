package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.MessageKnowledgeCitationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.MessageKnowledgeCitationMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Repository for persisted knowledge citations. */
@Repository
public class MessageKnowledgeCitationRepository extends CrudRepository<MessageKnowledgeCitationMapper, MessageKnowledgeCitationEntity> { }
