package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeRetrievalLogEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeRetrievalLogMapper;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeRetrievalLogRepository extends CrudRepository<KnowledgeRetrievalLogMapper, KnowledgeRetrievalLogEntity> {

}
