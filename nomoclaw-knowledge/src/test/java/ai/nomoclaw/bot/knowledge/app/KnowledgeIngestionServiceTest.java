package ai.nomoclaw.bot.knowledge.app;

import ai.nomoclaw.bot.knowledge.bm25.LexicalSearchStore;
import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.ingestion.DefaultDocumentParser.KnowledgeParseException;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentChunker;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser;
import ai.nomoclaw.bot.knowledge.ingestion.EmbeddingProvider;
import ai.nomoclaw.bot.knowledge.rerank.NoopReranker;
import ai.nomoclaw.bot.knowledge.vector.VectorStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeIngestionServiceTest {
    @TempDir
    Path storageRoot;

    private JdbcTemplate jdbc;
    private KnowledgeProperties properties;
    private RecordingVectorStore vectorStore;
    private RecordingLexicalStore lexicalStore;
    private KnowledgeService service;
    private Path documentPath;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:knowledge-ingestion;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        createSchema();
        properties = new KnowledgeProperties();
        properties.setStorageRoot(storageRoot);
        properties.getIngestion().setInitialRetryDelay(Duration.ofMillis(1));
        properties.getIngestion().setMaxRetryDelay(Duration.ofMillis(1));
        vectorStore = new RecordingVectorStore();
        lexicalStore = new RecordingLexicalStore();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        KnowledgeIngestionMetrics metrics = new KnowledgeIngestionMetrics(
                beanFactory.getBeanProvider(MeterRegistry.class));
        service = new KnowledgeService(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), properties,
                new FixedParser(), new FixedChunker(), new FixedEmbeddingProvider(), vectorStore, Runnable::run,
                null, null, lexicalStore, new NoopReranker(), metrics);
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
        vectorStore.onUpsert = () -> jdbc.update(
                "UPDATE knowledge_ingestion_job SET lease_token='new-owner' WHERE job_uid='job_test'");

        service.runIngestion("job_test", "token-1");

        assertEquals("RUNNING", jobValue("status"));
        assertEquals("", jdbc.queryForObject(
                "SELECT current_version_uid FROM knowledge_document WHERE document_uid='doc_test'", String.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunk WHERE status='READY'", Integer.class));
    }

    @Test
    void permanentParseFailureDoesNotRetry() {
        service = serviceWithParser(new FailingParser());

        service.runIngestion("job_test", "token-1");

        assertEquals("FAILED", jobValue("status"));
        assertEquals("INVALID_ENCODING", jobValue("failure_code"));
        assertEquals(0, jdbc.queryForObject("SELECT retryable FROM knowledge_ingestion_job", Integer.class));
    }

    private KnowledgeService serviceWithParser(DocumentParser parser) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        KnowledgeIngestionMetrics metrics = new KnowledgeIngestionMetrics(
                beanFactory.getBeanProvider(MeterRegistry.class));
        return new KnowledgeService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(
                new DriverManagerDataSource(
                        "jdbc:h2:mem:knowledge-ingestion;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""))),
                properties, parser, new FixedChunker(), new FixedEmbeddingProvider(), vectorStore, Runnable::run,
                null, null, lexicalStore, new NoopReranker(), metrics);
    }

    private void assertCompletedWithSingleChunk() {
        assertEquals("COMPLETED", jobValue("status"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE status='READY'", Integer.class));
        assertEquals(1, vectorStore.points.size());
        assertEquals(1, lexicalStore.chunks.size());
    }

    private void resetRunning(String token, int attempt) {
        jdbc.update("UPDATE knowledge_ingestion_job SET status='RUNNING',stage='QUEUED',lease_token=?,attempt_count=?,"
                        + "worker_id='worker',failure_code='',failure_message='',retryable=0,next_retry_time=NULL WHERE job_uid='job_test'",
                token, attempt);
    }

    private String jobValue(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM knowledge_ingestion_job WHERE job_uid='job_test'", String.class);
    }

    private void insertFixture(String token) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO knowledge_base VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, "kb_test", "Test", "", "ACTIVE", "provider", "model", 3, "collection", 500, 80,
                8, .35, 0, 0, now, now);
        jdbc.update("INSERT INTO knowledge_document VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, "doc_test", "kb_test", "doc.txt", "UPLOAD", "doc.txt", "text/plain", documentPath.toString(),
                FilesExists.size(documentPath), "checksum", "", "PROCESSING", "", "", 0, 0, now, now);
        jdbc.update("INSERT INTO knowledge_document_version VALUES(?,?,?,?,?,?,?,?,?,?)",
                1L, "ver_test", "doc_test", 1, "checksum", "1", "1", "provider:model:3", "PENDING", now);
        jdbc.update("INSERT INTO knowledge_ingestion_job(job_uid,knowledge_base_uid,document_uid,document_version_uid,status,stage,"
                        + "progress_percent,total_chunks,processed_chunks,attempt_count,failure_code,failure_message,worker_id,lease_token,"
                        + "retryable,created_time,updated_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "job_test", "kb_test", "doc_test", "ver_test", "RUNNING", "QUEUED", 0, 0, 0, 1,
                "", "", "worker", token, 0, now, now);
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE knowledge_base (id BIGINT PRIMARY KEY, knowledge_base_uid VARCHAR(64), name VARCHAR(255), description VARCHAR(1000), "
                + "status VARCHAR(32), embedding_provider_id VARCHAR(64), embedding_model_id VARCHAR(128), embedding_dimension INT, "
                + "vector_collection_name VARCHAR(128), chunk_size_tokens INT, chunk_overlap_tokens INT, retrieval_top_k INT, similarity_threshold DOUBLE, "
                + "document_count INT, chunk_count BIGINT, created_time TIMESTAMP, updated_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE knowledge_document (id BIGINT PRIMARY KEY, document_uid VARCHAR(64), knowledge_base_uid VARCHAR(64), display_name VARCHAR(255), "
                + "source_type VARCHAR(32), original_file_name VARCHAR(255), content_type VARCHAR(128), file_path VARCHAR(1024), size_bytes BIGINT, checksum_sha256 VARCHAR(64), "
                + "current_version_uid VARCHAR(64), status VARCHAR(32), failure_code VARCHAR(64), failure_message VARCHAR(1000), page_count INT, chunk_count INT, "
                + "created_time TIMESTAMP, updated_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE knowledge_document_version (id BIGINT PRIMARY KEY, document_version_uid VARCHAR(64), document_uid VARCHAR(64), version_no INT, "
                + "checksum_sha256 VARCHAR(64), parser_version VARCHAR(32), chunker_version VARCHAR(32), embedding_model_fingerprint VARCHAR(255), status VARCHAR(32), created_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE knowledge_ingestion_job (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, job_uid VARCHAR(64), knowledge_base_uid VARCHAR(64), "
                + "document_uid VARCHAR(64), document_version_uid VARCHAR(64), status VARCHAR(32), stage VARCHAR(32), progress_percent INT, total_chunks INT, processed_chunks INT, "
                + "attempt_count INT, failure_code VARCHAR(64), failure_message VARCHAR(1000), worker_id VARCHAR(128), lease_token VARCHAR(64), lease_until TIMESTAMP, "
                + "last_heartbeat_time TIMESTAMP, next_retry_time TIMESTAMP, retryable INT, started_time TIMESTAMP, finished_time TIMESTAMP, created_time TIMESTAMP, updated_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE knowledge_chunk (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, chunk_uid VARCHAR(64) UNIQUE, knowledge_base_uid VARCHAR(64), "
                + "document_uid VARCHAR(64), document_version_uid VARCHAR(64), chunk_index INT, content VARCHAR(4000), token_count INT, content_hash VARCHAR(64), "
                + "page_from INT, page_to INT, section_path VARCHAR(1024), char_start INT, char_end INT, vector_point_id VARCHAR(64), status VARCHAR(32), created_time TIMESTAMP)");
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
