package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeIngestionJobEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeIngestionJobMapper;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeIngestionJobRepository extends CrudRepository<KnowledgeIngestionJobMapper, KnowledgeIngestionJobEntity> {

}
