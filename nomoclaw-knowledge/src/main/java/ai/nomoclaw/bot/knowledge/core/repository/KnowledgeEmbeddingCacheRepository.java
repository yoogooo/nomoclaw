package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeEmbeddingCacheEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeEmbeddingCacheMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    /** Counts all cache entries. */
    public long countAll() {
        return lambdaQuery().count();
    }

    /** Lists the oldest cache entry IDs by last access time. */
    public List<Long> listOldestIds(int limit) {
        if (limit <= 0) return List.of();
        return lambdaQuery().select(KnowledgeEmbeddingCacheEntity::getId)
                .orderByAsc(KnowledgeEmbeddingCacheEntity::getLastAccessTime, KnowledgeEmbeddingCacheEntity::getId)
                .last("LIMIT " + limit)
                .list()
                .stream()
                .map(KnowledgeEmbeddingCacheEntity::getId)
                .toList();
    }
}
