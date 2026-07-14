package ai.nomoclaw.bot.knowledge.core.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentVersionEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentVersionMapper;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeDocumentVersionRepository extends CrudRepository<KnowledgeDocumentVersionMapper, KnowledgeDocumentVersionEntity> {

}
