package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeImportItemEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportItemMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for knowledge import items.
 */
@Repository
public class KnowledgeImportItemRepository extends CrudRepository<KnowledgeImportItemMapper, KnowledgeImportItemEntity> {

    /** Lists import items in creation order. */
    public List<KnowledgeImportItemEntity> listByBatch(String batchUid) {
        return lambdaQuery().eq(KnowledgeImportItemEntity::getBatchUid, batchUid)
                .orderByAsc(KnowledgeImportItemEntity::getId).list();
    }
}
