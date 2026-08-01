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
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeImportBatchEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeImportItemEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeIngestionJobEntity;
import ai.nomoclaw.bot.knowledge.core.entity.MessageKnowledgeCitationEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeBaseMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeChunkMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentVersionMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportBatchMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeImportItemMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeIngestionJobMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.MessageKnowledgeCitationMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.AgentKnowledgeBaseRelationMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.ConversationKnowledgeBaseRelationMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeAgentConversationMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeEmbeddingCacheMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeRetrievalLogMapper;
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
import ai.nomoclaw.bot.knowledge.ingestion.DocumentChunker;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser;
import ai.nomoclaw.bot.knowledge.ingestion.EmbeddingProvider;
import ai.nomoclaw.bot.knowledge.model.KnowledgeModels;
import ai.nomoclaw.bot.knowledge.rerank.NoopReranker;
import ai.nomoclaw.bot.knowledge.vector.VectorStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeUploadServiceTest {
    @TempDir
    Path storageRoot;

    private KnowledgeService service;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private KnowledgeDocumentMapper knowledgeDocumentMapper;
    private KnowledgeDocumentVersionMapper knowledgeDocumentVersionMapper;
    private KnowledgeChunkMapper knowledgeChunkMapper;
    private KnowledgeIngestionJobMapper knowledgeIngestionJobMapper;
    private KnowledgeImportBatchMapper knowledgeImportBatchMapper;
    private KnowledgeImportItemMapper knowledgeImportItemMapper;
    private MessageKnowledgeCitationMapper messageKnowledgeCitationMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:knowledge-upload-" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDatabaseSupport.migrateKnowledgeSchema(dataSource);
        TestRepositorySupport repositories = new TestRepositorySupport(dataSource);
        knowledgeBaseMapper = repositories.mapper(KnowledgeBaseMapper.class);
        knowledgeDocumentMapper = repositories.mapper(KnowledgeDocumentMapper.class);
        knowledgeDocumentVersionMapper = repositories.mapper(KnowledgeDocumentVersionMapper.class);
        knowledgeChunkMapper = repositories.mapper(KnowledgeChunkMapper.class);
        knowledgeIngestionJobMapper = repositories.mapper(KnowledgeIngestionJobMapper.class);
        knowledgeImportBatchMapper = repositories.mapper(KnowledgeImportBatchMapper.class);
        knowledgeImportItemMapper = repositories.mapper(KnowledgeImportItemMapper.class);
        messageKnowledgeCitationMapper = repositories.mapper(MessageKnowledgeCitationMapper.class);

        KnowledgeProperties properties = KnowledgePropertiesTestSupport.properties();
        properties.setStorageRoot(storageRoot);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        KnowledgeIngestionMetrics metrics = new KnowledgeIngestionMetrics(
                beanFactory.getBeanProvider(MeterRegistry.class));
        service = new KnowledgeService(properties, new TextParser(), new EmptyChunker(),
                new EmptyEmbeddingProvider(), new EmptyVectorStore(), Runnable::run,
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
                new EmptyLexicalStore(), new NoopReranker(), metrics,
                repositories.repository(KnowledgeEmbeddingCacheRepository.class, KnowledgeEmbeddingCacheMapper.class));

        LocalDateTime now = LocalDateTime.now();
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
        base.setSimilarityThreshold(0.35d);
        base.setDocumentCount(0);
        base.setChunkCount(0L);
        base.setCreatedTime(toDate(now));
        base.setUpdatedTime(toDate(now));
        knowledgeBaseMapper.insert(base);
    }

    @Test
    void reportsAcceptedDuplicateAndRejectedFilesIndependently() {
        MockMultipartFile accepted = new MockMultipartFile("files", "a.txt", "text/plain", "same content".getBytes());
        MockMultipartFile duplicate = new MockMultipartFile("files", "b.txt", "text/plain", "same content".getBytes());
        MockMultipartFile rejected = new MockMultipartFile("files", "c.exe", "application/octet-stream", new byte[]{1});

        KnowledgeModels.UploadResult result = service.upload("kb_test", List.of(accepted, duplicate, rejected));

        assertEquals(List.of("ACCEPTED", "DUPLICATE", "REJECTED"),
                result.items().stream().map(KnowledgeModels.UploadFileResult::outcome).toList());
        assertEquals(2, result.documents().size());
        assertEquals(1L, knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<>()));
        assertEquals(0L, knowledgeIngestionJobMapper.selectCount(new LambdaQueryWrapper<>()));
        KnowledgeImportBatchEntity batch = knowledgeImportBatchMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeImportBatchEntity>()
                        .eq(KnowledgeImportBatchEntity::getBatchUid, result.batchUid()));
        assertEquals("DRAFT", batch.getStatus());
    }

    @Test
    void createsEmptyDraftBeforeChoosingFiles() {
        KnowledgeModels.ImportBatch batch = service.createImportSession("kb_test");

        assertEquals("DRAFT", batch.status());
        assertEquals(List.of(), batch.items());
        assertEquals(0L, knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<>()));
        assertEquals(0L, knowledgeIngestionJobMapper.selectCount(new LambdaQueryWrapper<>()));
    }

    @Test
    void createsJobsOnlyAfterIdempotentBuildConfirmation() {
        KnowledgeModels.UploadResult upload = service.upload("kb_test", List.of(
                new MockMultipartFile("files", "a.txt", "text/plain", "content".getBytes())));

        assertEquals(0L, knowledgeDocumentVersionMapper.selectCount(new LambdaQueryWrapper<>()));
        KnowledgeModels.ImportBatch first = service.buildImportBatch("kb_test", upload.batchUid(),
                new KnowledgeModels.BuildRequest("STRUCTURED", 500, 80));
        KnowledgeModels.ImportBatch repeated = service.buildImportBatch("kb_test", upload.batchUid(),
                new KnowledgeModels.BuildRequest("STRUCTURED", 500, 80));

        assertEquals("BUILDING", first.status());
        assertEquals("TOKEN", first.chunkStrategy());
        assertEquals(first.batchUid(), repeated.batchUid());
        assertEquals(1L, knowledgeDocumentVersionMapper.selectCount(new LambdaQueryWrapper<>()));
        assertEquals(1L, knowledgeIngestionJobMapper.selectCount(new LambdaQueryWrapper<>()));
        assertThrows(Exception.class, () -> service.buildImportBatch("kb_test", upload.batchUid(),
                new KnowledgeModels.BuildRequest("STRUCTURED", 800, 120)));
    }

    @Test
    void smartStrategyUsesSystemManagedTokenGuardrails() {
        KnowledgeModels.UploadResult upload = service.upload("kb_test", List.of(
                new MockMultipartFile("files", "smart.txt", "text/plain", "content".getBytes())));

        KnowledgeModels.ImportBatch batch = service.buildImportBatch("kb_test", upload.batchUid(),
                new KnowledgeModels.BuildRequest("STRUCTURED", "SMART", 900, 300, null));
        KnowledgeDocumentVersionEntity version = knowledgeDocumentVersionMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentVersionEntity>());

        assertEquals("SMART", batch.chunkStrategy());
        assertEquals(500, batch.chunkSizeTokens());
        assertEquals(80, batch.chunkOverlapTokens());
        assertEquals("SMART", version.getChunkStrategy());
    }

    @Test
    void deletesReadyDocumentAndClearsIndexesAndMetadata() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Path file = storageRoot.resolve("ready.txt");

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setDocumentUid("doc_ready");
        document.setKnowledgeBaseUid("kb_test");
        document.setDisplayName("ready.txt");
        document.setSourceType("UPLOAD");
        document.setOriginalFileName("ready.txt");
        document.setContentType("text/plain");
        document.setFilePath(file.toString());
        document.setSizeBytes(10L);
        document.setChecksumSha256("sha");
        document.setCurrentVersionUid("ver_ready");
        document.setStatus("READY");
        document.setFailureCode("");
        document.setFailureMessage("");
        document.setPageCount(2);
        document.setChunkCount(3);
        document.setCreatedTime(toDate(now));
        document.setUpdatedTime(toDate(now));
        knowledgeDocumentMapper.insert(document);

        KnowledgeDocumentVersionEntity version = new KnowledgeDocumentVersionEntity();
        version.setDocumentVersionUid("ver_ready");
        version.setDocumentUid("doc_ready");
        version.setVersionNo(1);
        version.setChecksumSha256("sha");
        version.setParserVersion("p");
        version.setChunkerVersion("c");
        version.setParserMode("STRUCTURED");
        version.setChunkSizeTokens(500);
        version.setChunkOverlapTokens(80);
        version.setPreprocessingConfig("{}");
        version.setBuildMode("UPLOAD");
        version.setEmbeddingProviderId("provider");
        version.setEmbeddingModelId("model");
        version.setEmbeddingDimension(3);
        version.setEmbeddingModelFingerprint("fp");
        version.setParseWarnings("[]");
        version.setStatus("READY");
        version.setCreatedTime(toDate(now));
        knowledgeDocumentVersionMapper.insert(version);

        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setChunkUid("chunk_ready");
        chunk.setKnowledgeBaseUid("kb_test");
        chunk.setDocumentUid("doc_ready");
        chunk.setDocumentVersionUid("ver_ready");
        chunk.setChunkIndex(0);
        chunk.setContent("content");
        chunk.setTokenCount(1);
        chunk.setContentHash("hash");
        chunk.setPageFrom(1);
        chunk.setPageTo(1);
        chunk.setSectionPath("");
        chunk.setCharStart(0);
        chunk.setCharEnd(10);
        chunk.setVectorPointId("point");
        chunk.setStatus("READY");
        chunk.setCreatedTime(toDate(now));
        knowledgeChunkMapper.insert(chunk);

        KnowledgeIngestionJobEntity job = new KnowledgeIngestionJobEntity();
        job.setJobUid("job_ready");
        job.setKnowledgeBaseUid("kb_test");
        job.setDocumentUid("doc_ready");
        job.setDocumentVersionUid("ver_ready");
        job.setStatus("COMPLETED");
        job.setStage("COMPLETED");
        job.setProgressPercent(100);
        job.setTotalChunks(3);
        job.setProcessedChunks(3);
        job.setAttemptCount(1);
        job.setFailureCode("");
        job.setFailureMessage("");
        job.setRetryable(false);
        job.setStartedTime(toDate(now));
        job.setFinishedTime(toDate(now));
        job.setCreatedTime(toDate(now));
        job.setUpdatedTime(toDate(now));
        knowledgeIngestionJobMapper.insert(job);

        KnowledgeImportItemEntity item = new KnowledgeImportItemEntity();
        item.setItemUid("item_ready");
        item.setBatchUid("batch_ready");
        item.setDocumentUid("doc_ready");
        item.setDocumentVersionUid("ver_ready");
        item.setOriginalFileName("ready.txt");
        item.setMode("UPLOAD");
        item.setOutcome("ACCEPTED");
        item.setStatus("READY");
        item.setErrorCode("");
        item.setErrorMessage("");
        item.setCreatedTime(toDate(now));
        item.setUpdatedTime(toDate(now));
        knowledgeImportItemMapper.insert(item);

        MessageKnowledgeCitationEntity citation = new MessageKnowledgeCitationEntity();
        citation.setMessageUid("msg");
        citation.setAssistantMessageUid("");
        citation.setRetrievalUid("ret");
        citation.setChunkUid("chunk_ready");
        citation.setRankIndex(1);
        citation.setScore(0.8d);
        citation.setCreatedTime(toDate(now));
        messageKnowledgeCitationMapper.insert(citation);

        Files.writeString(file, "ready");

        service.deleteUploadedDocument("kb_test", "doc_ready");

        assertEquals(0L, knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocumentUid, "doc_ready")));
        assertEquals(0L, knowledgeDocumentVersionMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentVersionEntity>()
                .eq(KnowledgeDocumentVersionEntity::getDocumentUid, "doc_ready")));
        assertEquals(0L, knowledgeChunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocumentUid, "doc_ready")));
        assertEquals(0L, knowledgeIngestionJobMapper.selectCount(new LambdaQueryWrapper<KnowledgeIngestionJobEntity>()
                .eq(KnowledgeIngestionJobEntity::getDocumentUid, "doc_ready")));
        assertEquals(0L, knowledgeImportItemMapper.selectCount(new LambdaQueryWrapper<KnowledgeImportItemEntity>()
                .eq(KnowledgeImportItemEntity::getDocumentUid, "doc_ready")));
        assertEquals(0L, messageKnowledgeCitationMapper.selectCount(new LambdaQueryWrapper<MessageKnowledgeCitationEntity>()
                .eq(MessageKnowledgeCitationEntity::getChunkUid, "chunk_ready")));
        assertFalse(Files.exists(file));
    }

    private Date toDate(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private static final class TextParser implements DocumentParser {
        @Override
        public boolean supports(String contentType, String fileName) {
            return fileName.endsWith(".txt");
        }

        @Override
        public ParsedDocument parse(Path file) {
            return new ParsedDocument(List.of());
        }
    }

    private static final class EmptyChunker implements DocumentChunker {
        @Override
        public List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens) {
            return List.of();
        }
    }

    private static final class EmptyEmbeddingProvider implements EmbeddingProvider {
        @Override
        public List<List<Float>> embed(List<String> texts, String providerId, String modelId, int expectedDimension) {
            return List.of();
        }
    }

    private static final class EmptyVectorStore implements VectorStore {
        @Override
        public void ensureCollection(String collection, int dimension) {
        }

        @Override
        public void upsert(String collection, List<Point> points) {
        }

        @Override
        public List<Hit> search(String collection, List<Float> vector, List<String> knowledgeBaseUids, int limit) {
            return List.of();
        }

        @Override
        public void deleteByDocument(String collection, String documentUid) {
        }

        @Override
        public void deleteByDocumentVersion(String collection, String documentVersionUid) {
        }

        @Override
        public boolean available() {
            return true;
        }
    }

    private static final class EmptyLexicalStore implements LexicalSearchStore {
        @Override
        public void replaceDocument(String knowledgeBaseUid, String documentUid, String documentVersionUid,
                                    String documentName, List<IndexedChunk> chunks) {
        }

        @Override
        public List<Hit> search(String query, List<String> knowledgeBaseUids, int limit) {
            return List.of();
        }

        @Override
        public void deleteByDocument(String documentUid) {
        }

        @Override
        public void deleteByKnowledgeBase(String knowledgeBaseUid) {
        }

        @Override
        public void clear() {
        }

        @Override
        public boolean available() {
            return false;
        }
    }
}
