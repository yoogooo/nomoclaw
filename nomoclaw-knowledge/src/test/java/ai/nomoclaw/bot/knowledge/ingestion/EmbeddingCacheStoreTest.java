package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.TestDatabaseSupport;
import ai.nomoclaw.bot.knowledge.TestRepositorySupport;
import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.config.KnowledgePropertiesTestSupport;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeEmbeddingCacheEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeEmbeddingCacheMapper;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeEmbeddingCacheRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingCacheStoreTest {
    private KnowledgeEmbeddingCacheMapper cacheMapper;
    private KnowledgeProperties properties;
    private EmbeddingCacheStore cache;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:embedding-cache-" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDatabaseSupport.migrateKnowledgeSchema(dataSource);
        properties = KnowledgePropertiesTestSupport.properties();
        TestRepositorySupport repositories = new TestRepositorySupport(dataSource);
        cacheMapper = repositories.mapper(KnowledgeEmbeddingCacheMapper.class);
        cache = new EmbeddingCacheStore(repositories.repository(KnowledgeEmbeddingCacheRepository.class,
                KnowledgeEmbeddingCacheMapper.class), properties);
    }

    @Test
    void persistsAndReadsFloatVectorsWithinFingerprintNamespace() {
        String key = cache.key("provider:model:3", "content");
        cache.put(key, "provider:model:3", "content", List.of(1F, 2F, 3F));

        assertThat(cache.get(key, 3)).contains(List.of(1F, 2F, 3F));
        assertThat(cache.get(cache.key("other:model:3", "content"), 3)).isEmpty();
        assertThat(cacheMapper.selectOne(new LambdaQueryWrapper<KnowledgeEmbeddingCacheEntity>()).getHitCount())
                .isEqualTo(1L);
    }

    @Test
    void removesExpiredAndLeastRecentlyUsedEntries() {
        properties.getEmbeddingCache().setTtl(Duration.ofDays(1));
        properties.getEmbeddingCache().setMaxEntries(1);
        cache.put(cache.key("model", "old"), "model", "old", List.of(1F));
        cacheMapper.update(new LambdaUpdateWrapper<KnowledgeEmbeddingCacheEntity>()
                .set(KnowledgeEmbeddingCacheEntity::getLastAccessTime,
                        Timestamp.valueOf(LocalDateTime.now().minusDays(2))));
        cache.put(cache.key("model", "new"), "model", "new", List.of(2F));

        cache.cleanup();

        assertThat(cacheMapper.selectCount(new LambdaQueryWrapper<>())).isEqualTo(1L);
        assertThat(cacheMapper.selectOne(new LambdaQueryWrapper<KnowledgeEmbeddingCacheEntity>()).getContentHash())
                .isEqualTo("new");
    }
}
