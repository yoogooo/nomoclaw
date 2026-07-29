package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeEmbeddingCacheEntity;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeEmbeddingCacheRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Persistent, disposable embedding cache shared by ingestion retries and documents.
 */
@Component
public class EmbeddingCacheStore {
    private final KnowledgeEmbeddingCacheRepository repository;
    private final KnowledgeProperties properties;

    public EmbeddingCacheStore(KnowledgeEmbeddingCacheRepository repository, KnowledgeProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public String key(String modelFingerprint, String contentHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (modelFingerprint + ":" + contentHash).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create embedding cache key", ex);
        }
    }

    public Optional<List<Float>> get(String cacheKey, int dimension) {
        if (!properties.getEmbeddingCache().isEnabled()) return Optional.empty();
        KnowledgeEmbeddingCacheEntity entity = repository.findByKeyAndDimension(cacheKey, dimension);
        if (entity == null || entity.getVectorBlob() == null) return Optional.empty();
        LocalDateTime now = LocalDateTime.now();
        entity.setHitCount(entity.getHitCount() == null ? 1L : entity.getHitCount() + 1);
        entity.setLastAccessTime(toDate(now));
        repository.updateById(entity);
        return Optional.of(decode(entity.getVectorBlob(), dimension));
    }

    public void put(String cacheKey, String modelFingerprint, String contentHash, List<Float> vector) {
        if (!properties.getEmbeddingCache().isEnabled()) return;
        byte[] bytes = encode(vector);
        LocalDateTime now = LocalDateTime.now();
        try {
            KnowledgeEmbeddingCacheEntity entity = new KnowledgeEmbeddingCacheEntity();
            entity.setCacheKey(cacheKey);
            entity.setModelFingerprint(modelFingerprint);
            entity.setContentHash(contentHash);
            entity.setDimension(vector.size());
            entity.setVectorBlob(bytes);
            entity.setVectorBytes(bytes.length);
            entity.setHitCount(0L);
            entity.setCreatedTime(toDate(now));
            entity.setLastAccessTime(toDate(now));
            repository.save(entity);
        } catch (DuplicateKeyException ignored) {
            KnowledgeEmbeddingCacheEntity entity = repository.findByKeyAndDimension(cacheKey, vector.size());
            if (entity != null) {
                entity.setLastAccessTime(toDate(now));
                repository.updateById(entity);
            }
        }
    }

    public void cleanup() {
        if (!properties.getEmbeddingCache().isEnabled()) return;
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getEmbeddingCache().getTtl());
        repository.lambdaUpdate().lt(KnowledgeEmbeddingCacheEntity::getLastAccessTime, toDate(cutoff)).remove();
        int overflow = Math.max(0, Math.toIntExact(repository.countAll()) - properties.getEmbeddingCache().getMaxEntries());
        if (overflow > 0) {
            for (Long id : repository.listOldestIds(overflow)) {
                repository.removeById(id);
            }
        }
    }

    private Date toDate(LocalDateTime value) {
        return java.sql.Timestamp.valueOf(value);
    }

    private byte[] encode(List<Float> vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.size() * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        vector.forEach(buffer::putFloat);
        return buffer.array();
    }

    private List<Float> decode(byte[] bytes, int dimension) {
        if (bytes.length != dimension * Float.BYTES) {
            throw new IllegalStateException("Cached embedding dimension mismatch");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        List<Float> vector = new ArrayList<>(dimension);
        while (buffer.hasRemaining()) vector.add(buffer.getFloat());
        return vector;
    }
}
