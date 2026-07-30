package ai.nomoclaw.bot.knowledge.app;

import ai.nomoclaw.bot.knowledge.TestDatabaseSupport;
import ai.nomoclaw.bot.knowledge.TestRepositorySupport;
import ai.nomoclaw.bot.knowledge.bm25.LexicalSearchStore;
import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.config.KnowledgePropertiesTestSupport;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeBaseEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeChunkEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentVersionEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeIngestionJobEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.AgentKnowledgeBaseRelationMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.ConversationKnowledgeBaseRelationMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeAgentConversationMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeBaseMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeChunkMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentVersionMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeEmbeddingCacheMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportBatchMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportItemMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeIngestionJobMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeRetrievalLogMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.MessageKnowledgeCitationMapper;
import ai.nomoclaw.bot.knowledge.core.repository.AgentKnowledgeBaseRelationRepository;
import ai.nomoclaw.bot.knowledge.core.repository.ConversationKnowledgeBaseRelationRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeAgentConversationRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeBaseRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeChunkRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeDocumentRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeDocumentVersionRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeEmbeddingCacheRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeImportBatchRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeImportItemRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeIngestionJobRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeRetrievalLogRepository;
import ai.nomoclaw.bot.knowledge.core.repository.MessageKnowledgeCitationRepository;
import ai.nomoclaw.bot.knowledge.ingestion.DefaultDocumentParser.KnowledgeParseException;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentChunker;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser;
import ai.nomoclaw.bot.knowledge.ingestion.EmbeddingProvider;
import ai.nomoclaw.bot.knowledge.rerank.NoopReranker;
import ai.nomoclaw.bot.knowledge.vector.VectorStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeIngestionServiceTest {
    @TempDir
    Path storageRoot;

    private KnowledgeProperties properties;
    private RecordingVectorStore vectorStore;
    private RecordingLexicalStore lexicalStore;
    private KnowledgeService service;
    private Path documentPath;
    private TestRepositorySupport repositories;
    private KnowledgeBaseMapper baseMapper;
    private KnowledgeDocumentMapper documentMapper;
    private KnowledgeDocumentVersionMapper documentVersionMapper;
    private KnowledgeIngestionJobMapper ingestionJobMapper;
    private KnowledgeChunkMapper chunkMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:knowledge-ingestion-" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDatabaseSupport.migrateKnowledgeSchema(dataSource);
        repositories = new TestRepositorySupport(dataSource);
        baseMapper = repositories.mapper(KnowledgeBaseMapper.class);
        documentMapper = repositories.mapper(KnowledgeDocumentMapper.class);
        documentVersionMapper = repositories.mapper(KnowledgeDocumentVersionMapper.class);
        ingestionJobMapper = repositories.mapper(KnowledgeIngestionJobMapper.class);
        chunkMapper = repositories.mapper(KnowledgeChunkMapper.class);
        properties = KnowledgePropertiesTestSupport.properties();
        properties.setStorageRoot(storageRoot);
        properties.getEmbeddingCache().setEnabled(false);
        properties.getIngestion().setInitialRetryDelay(Duration.ofMillis(1));
        properties.getIngestion().setMaxRetryDelay(Duration.ofMillis(1));
        vectorStore = new RecordingVectorStore();
        lexicalStore = new RecordingLexicalStore();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        KnowledgeIngestionMetrics metrics = new KnowledgeIngestionMetrics(
                beanFactory.getBeanProvider(MeterRegistry.class));
        service = serviceWithParser(new FixedParser());
        documentPath = storageRoot.resolve("doc.txt");
        Files.writeString(documentPath, "knowledge ingestion test content");
        insertFixture("token-1");
    }

    @Test
    void repeatedExecutionKeepsOneChunkAndOnePoint() {
        service.runIngestion("job_test", "token-1");
        assertCompletedWithSingleChunk();

        resetRunning("token-2", 2);
        service.runIngestion("job_test", "token-2");

        assertCompletedWithSingleChunk();
    }

    @Test
    void transientVectorFailureRetriesAndThenCompletesWithoutDuplicates() {
        vectorStore.failNextUpsert = true;
        service.runIngestion("job_test", "token-1");
        assertEquals("RETRY_WAIT", jobValue("status"));
        assertEquals("TEMPORARY_INGESTION_FAILURE", jobValue("failure_code"));

        resetRunning("token-2", 2);
        service.runIngestion("job_test", "token-2");

        assertCompletedWithSingleChunk();
    }

    @Test
    void workerThatLosesLeaseCannotPublish() {
        vectorStore.onUpsert = () -> ingestionJobMapper.update(new LambdaUpdateWrapper<KnowledgeIngestionJobEntity>()
                .set(KnowledgeIngestionJobEntity::getLeaseToken, "new-owner")
                .eq(KnowledgeIngestionJobEntity::getJobUid, "job_test"));

        service.runIngestion("job_test", "token-1");

        assertEquals("RUNNING", jobValue("status"));
        assertEquals("", documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocumentUid, "doc_test")).getCurrentVersionUid());
        assertEquals(0L, chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getStatus, "READY")));
    }

    @Test
    void permanentParseFailureDoesNotRetry() {
        service = serviceWithParser(new FailingParser());

        service.runIngestion("job_test", "token-1");

        assertEquals("FAILED", jobValue("status"));
        assertEquals("INVALID_ENCODING", jobValue("failure_code"));
        assertEquals(false, job().getRetryable());
    }

    private KnowledgeService serviceWithParser(DocumentParser parser) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        KnowledgeIngestionMetrics metrics = new KnowledgeIngestionMetrics(
                beanFactory.getBeanProvider(MeterRegistry.class));
        try {
            return new KnowledgeService(properties, parser, new FixedChunker(), new FixedEmbeddingProvider(),
                    vectorStore, Runnable::run,
                    repositories.repository(KnowledgeBaseRepository.class, KnowledgeBaseMapper.class),
                    repositories.repository(KnowledgeChunkRepository.class, KnowledgeChunkMapper.class),
                    repositories.repository(KnowledgeDocumentRepository.class, KnowledgeDocumentMapper.class),
                    repositories.repository(KnowledgeDocumentVersionRepository.class, KnowledgeDocumentVersionMapper.class),
                    repositories.repository(KnowledgeImportBatchRepository.class, KnowledgeImportBatchMapper.class),
                    repositories.repository(KnowledgeIngestionJobRepository.class, KnowledgeIngestionJobMapper.class),
                    repositories.repository(KnowledgeImportItemRepository.class, KnowledgeImportItemMapper.class),
                    repositories.repository(KnowledgeRetrievalLogRepository.class, KnowledgeRetrievalLogMapper.class),
                    repositories.repository(MessageKnowledgeCitationRepository.class, MessageKnowledgeCitationMapper.class),
                    repositories.repository(ConversationKnowledgeBaseRelationRepository.class, ConversationKnowledgeBaseRelationMapper.class),
                    repositories.repository(AgentKnowledgeBaseRelationRepository.class, AgentKnowledgeBaseRelationMapper.class),
                    repositories.repository(KnowledgeAgentConversationRepository.class, KnowledgeAgentConversationMapper.class),
                    lexicalStore, new NoopReranker(), metrics,
                    repositories.repository(KnowledgeEmbeddingCacheRepository.class, KnowledgeEmbeddingCacheMapper.class));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void assertCompletedWithSingleChunk() {
        assertEquals("COMPLETED", jobValue("status"));
        assertEquals(1L, chunkMapper.selectCount(new LambdaQueryWrapper<>()));
        assertEquals(1L, chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getStatus, "READY")));
        assertEquals(1, vectorStore.points.size());
        assertEquals(1, lexicalStore.chunks.size());
    }

    private void resetRunning(String token, int attempt) {
        ingestionJobMapper.update(new LambdaUpdateWrapper<KnowledgeIngestionJobEntity>()
                .set(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .set(KnowledgeIngestionJobEntity::getStage, "QUEUED")
                .set(KnowledgeIngestionJobEntity::getLeaseToken, token)
                .set(KnowledgeIngestionJobEntity::getAttemptCount, attempt)
                .set(KnowledgeIngestionJobEntity::getWorkerId, "worker")
                .set(KnowledgeIngestionJobEntity::getFailureCode, "")
                .set(KnowledgeIngestionJobEntity::getFailureMessage, "")
                .set(KnowledgeIngestionJobEntity::getRetryable, false)
                .set(KnowledgeIngestionJobEntity::getNextRetryTime, null)
                .eq(KnowledgeIngestionJobEntity::getJobUid, "job_test"));
    }

    private String jobValue(String column) {
        KnowledgeIngestionJobEntity job = job();
        return switch (column) {
            case "status" -> job.getStatus();
            case "failure_code" -> job.getFailureCode();
            default -> throw new IllegalArgumentException("Unsupported job column: " + column);
        };
    }

    private void insertFixture(String token) {
        LocalDateTime now = LocalDateTime.now();
        Date date = toDate(now);
        KnowledgeBaseEntity base = new KnowledgeBaseEntity();
        base.setKnowledgeBaseUid("kb_test");
        base.setName("Test");
        base.setDescription("");
        base.setStatus("ACTIVE");
        base.setEmbeddingProviderId("provider");
        base.setEmbeddingModelId("model");
        base.setEmbeddingDimension(3);
        base.setVectorCollectionName("collection");
        base.setChunkSizeTokens(500);
        base.setChunkOverlapTokens(80);
        base.setRetrievalTopK(8);
        base.setSimilarityThreshold(.35);
        base.setDocumentCount(0);
        base.setChunkCount(0L);
        base.setCreatedTime(date);
        base.setUpdatedTime(date);
        baseMapper.insert(base);

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setDocumentUid("doc_test");
        document.setKnowledgeBaseUid("kb_test");
        document.setDisplayName("doc.txt");
        document.setSourceType("UPLOAD");
        document.setOriginalFileName("doc.txt");
        document.setContentType("text/plain");
        document.setFilePath(documentPath.toString());
        document.setSizeBytes(FilesExists.size(documentPath));
        document.setChecksumSha256("checksum");
        document.setCurrentVersionUid("");
        document.setStatus("PROCESSING");
        document.setFailureCode("");
        document.setFailureMessage("");
        document.setPageCount(0);
        document.setChunkCount(0);
        document.setCreatedTime(date);
        document.setUpdatedTime(date);
        documentMapper.insert(document);

        KnowledgeDocumentVersionEntity version = new KnowledgeDocumentVersionEntity();
        version.setDocumentVersionUid("ver_test");
        version.setDocumentUid("doc_test");
        version.setVersionNo(1);
        version.setChecksumSha256("checksum");
        version.setParserVersion("1");
        version.setChunkerVersion("1");
        version.setParserMode("STRUCTURED");
        version.setChunkSizeTokens(500);
        version.setChunkOverlapTokens(80);
        version.setPreprocessingConfig(null);
        version.setBuildMode("INITIAL");
        version.setEmbeddingProviderId("provider");
        version.setEmbeddingModelId("model");
        version.setEmbeddingDimension(3);
        version.setEmbeddingModelFingerprint("provider:model:3");
        version.setParseWarnings(null);
        version.setStatus("PENDING");
        version.setCreatedTime(date);
        documentVersionMapper.insert(version);

        KnowledgeIngestionJobEntity job = new KnowledgeIngestionJobEntity();
        job.setJobUid("job_test");
        job.setKnowledgeBaseUid("kb_test");
        job.setDocumentUid("doc_test");
        job.setDocumentVersionUid("ver_test");
        job.setStatus("RUNNING");
        job.setStage("QUEUED");
        job.setProgressPercent(0);
        job.setTotalChunks(0);
        job.setProcessedChunks(0);
        job.setProcessedPages(0);
        job.setTotalPages(0);
        job.setCacheHitChunks(0);
        job.setCacheMissChunks(0);
        job.setAttemptCount(1);
        job.setFailureCode("");
        job.setFailureMessage("");
        job.setWorkerId("worker");
        job.setLeaseToken(token);
        job.setRetryable(false);
        job.setCreatedTime(date);
        job.setUpdatedTime(date);
        ingestionJobMapper.insert(job);
    }

    private KnowledgeIngestionJobEntity job() {
        return ingestionJobMapper.selectOne(new LambdaQueryWrapper<KnowledgeIngestionJobEntity>()
                .eq(KnowledgeIngestionJobEntity::getJobUid, "job_test"));
    }

    private Date toDate(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private static final class FixedParser implements DocumentParser {
        @Override public boolean supports(String contentType, String fileName) { return true; }
        @Override public ParsedDocument parse(Path file) { return new ParsedDocument(List.of(new Page(1, "Section", "knowledge ingestion test content"))); }
    }

    private static final class FailingParser implements DocumentParser {
        @Override public boolean supports(String contentType, String fileName) { return true; }
        @Override public ParsedDocument parse(Path file) {
            throw new KnowledgeParseException("INVALID_ENCODING", "invalid encoding");
        }
    }

    private static final class FixedChunker implements DocumentChunker {
        @Override public List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens) {
            return List.of(new Chunk(0, document.pages().get(0).text(), 6, 1, 1, "Section", 0, 32));
        }
    }

    private static final class FixedEmbeddingProvider implements EmbeddingProvider {
        @Override public List<List<Float>> embed(List<String> texts, String providerId, String modelId, int expectedDimension) {
            return texts.stream().map(text -> List.of(1F, 0F, 0F)).toList();
        }
    }

    private static final class RecordingVectorStore implements VectorStore {
        private final Map<String, Point> points = new LinkedHashMap<>();
        private boolean failNextUpsert;
        private Runnable onUpsert = () -> { };

        @Override public void ensureCollection(String collection, int dimension) { }
        @Override public void upsert(String collection, List<Point> values) {
            if (failNextUpsert) {
                failNextUpsert = false;
                throw new IllegalStateException("Qdrant HTTP 503");
            }
            values.forEach(point -> points.put(point.id(), point));
            onUpsert.run();
        }
        @Override public List<Hit> search(String collection, List<Float> vector, List<String> knowledgeBaseUids, int limit) { return List.of(); }
        @Override public void deleteByDocument(String collection, String documentUid) { points.clear(); }
        @Override public void deleteByDocumentVersion(String collection, String documentVersionUid) { points.clear(); }
        @Override public boolean available() { return true; }
    }

    private static final class RecordingLexicalStore implements LexicalSearchStore {
        private List<IndexedChunk> chunks = List.of();
        @Override public void replaceDocument(String knowledgeBaseUid, String documentUid, String documentVersionUid, String documentName, List<IndexedChunk> values) { chunks = List.copyOf(values); }
        @Override public List<Hit> search(String query, List<String> knowledgeBaseUids, int limit) { return List.of(); }
        @Override public void deleteByDocument(String documentUid) { chunks = List.of(); }
        @Override public void deleteByKnowledgeBase(String knowledgeBaseUid) { chunks = List.of(); }
        @Override public void clear() { chunks = List.of(); }
        @Override public boolean available() { return true; }
    }

    private static final class FilesExists {
        private static long size(Path path) {
            try {
                return Files.size(path);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
