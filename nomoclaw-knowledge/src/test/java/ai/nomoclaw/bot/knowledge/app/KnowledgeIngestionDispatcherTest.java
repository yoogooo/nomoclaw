package ai.nomoclaw.bot.knowledge.app;

import ai.nomoclaw.bot.knowledge.TestRepositorySupport;
import ai.nomoclaw.bot.knowledge.TestDatabaseSupport;
import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.config.KnowledgePropertiesTestSupport;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeIngestionJobEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeDocumentMapper;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeIngestionJobMapper;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeDocumentRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeIngestionJobRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class KnowledgeIngestionDispatcherTest {
    private KnowledgeProperties properties;
    private ListRecordingRunner runner;
    private ThreadPoolTaskScheduler scheduler;
    private KnowledgeIngestionDispatcher dispatcher;
    private KnowledgeIngestionJobRepository ingestionJobRepository;
    private KnowledgeDocumentRepository documentRepository;
    private KnowledgeIngestionJobMapper ingestionJobMapper;
    private KnowledgeDocumentMapper documentMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:knowledge-dispatcher-" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDatabaseSupport.migrateKnowledgeSchema(dataSource);
        TestRepositorySupport repositories = new TestRepositorySupport(dataSource);
        ingestionJobMapper = repositories.mapper(KnowledgeIngestionJobMapper.class);
        documentMapper = repositories.mapper(KnowledgeDocumentMapper.class);
        ingestionJobRepository = repositories.repository(KnowledgeIngestionJobRepository.class, KnowledgeIngestionJobMapper.class);
        documentRepository = repositories.repository(KnowledgeDocumentRepository.class, KnowledgeDocumentMapper.class);
        properties = KnowledgePropertiesTestSupport.properties();
        properties.getIngestion().setWorkerCount(2);
        properties.getIngestion().setLeaseSeconds(30);
        properties.getIngestion().setDispatchInterval(Duration.ofMillis(20));
        runner = new ListRecordingRunner();
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("knowledge-dispatcher-test-");
        scheduler.initialize();
        Executor directExecutor = Runnable::run;
        dispatcher = new KnowledgeIngestionDispatcher(ingestionJobRepository, documentRepository,
                properties, runner, directExecutor, scheduler);
    }

    @AfterEach
    void tearDown() {
        dispatcher.stop();
        scheduler.destroy();
    }

    @Test
    void claimsPendingAndExpiredRunningJobs() {
        insertJob("job_pending", "doc_pending", "PENDING", 0, null);
        insertJob("job_expired", "doc_expired", "RUNNING", 1, LocalDateTime.now().minusMinutes(1));

        dispatcher.start();

        waitForInvocationCount(runner, 2);
        assertEquals(2L, ingestionJobMapper.selectCount(new LambdaQueryWrapper<KnowledgeIngestionJobEntity>()
                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .isNotNull(KnowledgeIngestionJobEntity::getWorkerId)
                .isNotNull(KnowledgeIngestionJobEntity::getLeaseToken)));
    }

    @Test
    void exhaustedExpiredJobBecomesFailed() {
        properties.getIngestion().setMaxAttempts(3);
        insertDocument("doc_exhausted");
        insertJob("job_exhausted", "doc_exhausted", "RUNNING", 3, LocalDateTime.now().minusMinutes(1));

        dispatcher.start();

        waitUntilFailed("job_exhausted");
        assertEquals(0, runner.count());
        assertEquals("FAILED", findDocument("doc_exhausted").getStatus());
        assertEquals("LEASE_EXPIRED", findJob("job_exhausted").getFailureCode());
    }

    @Test
    void activeLeaseIsNotClaimedTwice() {
        insertJob("job_active", "doc_active", "RUNNING", 1, LocalDateTime.now().plusMinutes(1));

        dispatcher.start();

        sleep(200);
        assertEquals(0, runner.count());
        assertEquals(1, findJob("job_active").getAttemptCount());
    }

    @Test
    void competingDispatchersClaimJobOnlyOnce() throws Exception {
        insertJob("job_competing", "doc_competing", "PENDING", 0, null);
        ListRecordingRunner otherRunner = new ListRecordingRunner();
        KnowledgeIngestionDispatcher other = new KnowledgeIngestionDispatcher(
                ingestionJobRepository, documentRepository, properties, otherRunner, Runnable::run, scheduler);
        try {
            dispatcher.start();
            other.start();
            Thread.sleep(250);
        } finally {
            other.stop();
        }

        int invocations = runner.count() + otherRunner.count();
        assertEquals(1, invocations);
        assertEquals(1, findJob("job_competing").getAttemptCount());
    }

    private void insertJob(String jobUid, String documentUid, String status, int attemptCount,
                           LocalDateTime leaseUntil) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeIngestionJobEntity job = new KnowledgeIngestionJobEntity();
        job.setJobUid(jobUid);
        job.setKnowledgeBaseUid("kb_" + documentUid);
        job.setDocumentUid(documentUid);
        job.setDocumentVersionUid("ver_" + documentUid);
        job.setStatus(status);
        job.setStage("QUEUED");
        job.setProgressPercent(0);
        job.setTotalChunks(0);
        job.setProcessedChunks(0);
        job.setProcessedPages(0);
        job.setTotalPages(0);
        job.setCacheHitChunks(0);
        job.setCacheMissChunks(0);
        job.setAttemptCount(attemptCount);
        job.setFailureCode("");
        job.setFailureMessage("");
        job.setLeaseUntil(leaseUntil == null ? null : Timestamp.valueOf(leaseUntil));
        job.setRetryable(false);
        job.setCreatedTime(Timestamp.valueOf(now));
        job.setUpdatedTime(Timestamp.valueOf(now));
        ingestionJobMapper.insert(job);
    }

    private void insertDocument(String documentUid) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setDocumentUid(documentUid);
        document.setKnowledgeBaseUid("kb_" + documentUid);
        document.setDisplayName(documentUid + ".txt");
        document.setSourceType("UPLOAD");
        document.setOriginalFileName(documentUid + ".txt");
        document.setContentType("text/plain");
        document.setFilePath("");
        document.setSizeBytes(0L);
        document.setChecksumSha256("checksum_" + documentUid);
        document.setCurrentVersionUid("");
        document.setStatus("PROCESSING");
        document.setFailureCode("");
        document.setFailureMessage("");
        document.setPageCount(0);
        document.setChunkCount(0);
        document.setCreatedTime(Timestamp.valueOf(now));
        document.setUpdatedTime(Timestamp.valueOf(now));
        documentMapper.insert(document);
    }

    private void waitUntilFailed(String jobUid) {
        for (int attempt = 0; attempt < 50; attempt++) {
            if ("FAILED".equals(findJob(jobUid).getStatus())) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        fail("job did not reach FAILED: " + jobUid);
    }

    private KnowledgeIngestionJobEntity findJob(String jobUid) {
        return ingestionJobMapper.selectOne(new LambdaQueryWrapper<KnowledgeIngestionJobEntity>()
                .eq(KnowledgeIngestionJobEntity::getJobUid, jobUid));
    }

    private KnowledgeDocumentEntity findDocument(String documentUid) {
        return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocumentUid, documentUid));
    }

    private void waitForInvocationCount(ListRecordingRunner target, int expected) {
        for (int attempt = 0; attempt < 75; attempt++) {
            if (target.count() >= expected) return;
            sleep(20);
        }
        fail("runner invocation count did not reach " + expected);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static final class ListRecordingRunner implements KnowledgeIngestionRunner {
        private final CopyOnWriteArrayList<String> jobUids = new CopyOnWriteArrayList<>();

        @Override
        public void runIngestion(String jobUid, String leaseToken) {
            jobUids.add(jobUid);
        }

        private int count() {
            return jobUids.size();
        }
    }
}
