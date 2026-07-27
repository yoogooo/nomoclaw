package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeEmbeddingCacheEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeEmbeddingCacheMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for disposable embedding cache entries.
 */
@Repository
public class KnowledgeEmbeddingCacheRepository extends CrudRepository<KnowledgeEmbeddingCacheMapper, KnowledgeEmbeddingCacheEntity> {

    /** Finds one cache entry by cache key and vector dimension. */
    public KnowledgeEmbeddingCacheEntity findByKeyAndDimension(String cacheKey, int dimension) {
        return lambdaQuery().eq(KnowledgeEmbeddingCacheEntity::getCacheKey, cacheKey)
                .eq(KnowledgeEmbeddingCacheEntity::getDimension, dimension).one();
    }
}
