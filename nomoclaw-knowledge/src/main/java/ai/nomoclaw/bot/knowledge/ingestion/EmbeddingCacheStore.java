package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgePersistenceRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Persistent, disposable embedding cache shared by ingestion retries and documents.
 */
@Component
public class EmbeddingCacheStore {
    private final KnowledgePersistenceRepository persistence;
    private final KnowledgeProperties properties;

    public EmbeddingCacheStore(KnowledgePersistenceRepository persistence, KnowledgeProperties properties) {
        this.persistence = persistence;
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
        List<byte[]> values = persistence.query("SELECT vector_blob FROM knowledge_embedding_cache "
                        + "WHERE cache_key=? AND dimension=?",
                (rs, row) -> rs.getBytes(1), cacheKey, dimension);
        if (values.isEmpty()) return Optional.empty();
        LocalDateTime now = LocalDateTime.now();
        persistence.update("UPDATE knowledge_embedding_cache SET hit_count=hit_count+1,last_access_time=? WHERE cache_key=?",
                now, cacheKey);
        return Optional.of(decode(values.get(0), dimension));
    }

    public void put(String cacheKey, String modelFingerprint, String contentHash, List<Float> vector) {
        if (!properties.getEmbeddingCache().isEnabled()) return;
        byte[] bytes = encode(vector);
        LocalDateTime now = LocalDateTime.now();
        try {
            persistence.update("INSERT INTO knowledge_embedding_cache(cache_key,model_fingerprint,content_hash,dimension,"
                            + "vector_blob,vector_bytes,hit_count,created_time,last_access_time) VALUES(?,?,?,?,?,?,?,?,?)",
                    cacheKey, modelFingerprint, contentHash, vector.size(), bytes, bytes.length, 0, now, now);
        } catch (DuplicateKeyException ignored) {
            persistence.update("UPDATE knowledge_embedding_cache SET last_access_time=? WHERE cache_key=?", now, cacheKey);
        }
    }

    public void cleanup() {
        if (!properties.getEmbeddingCache().isEnabled()) return;
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getEmbeddingCache().getTtl());
        persistence.update("DELETE FROM knowledge_embedding_cache WHERE last_access_time<?", cutoff);
        Integer count = persistence.queryForObject("SELECT COUNT(*) FROM knowledge_embedding_cache", Integer.class);
        int overflow = Math.max(0, (count == null ? 0 : count) - properties.getEmbeddingCache().getMaxEntries());
        if (overflow > 0) {
            List<Long> ids = persistence.queryForList("SELECT id FROM knowledge_embedding_cache "
                    + "ORDER BY last_access_time,id LIMIT ?", Long.class, overflow);
            for (Long id : ids) persistence.update("DELETE FROM knowledge_embedding_cache WHERE id=?", id);
        }
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
