package ai.nomoclaw.bot.knowledge.app;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeIngestionJobEntity;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeDocumentRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeIngestionJobRepository;
import ai.nomoclaw.bot.knowledge.util.UuidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Claims persisted ingestion jobs with portable compare-and-set updates and renews their leases.
 */
@Component
public class KnowledgeIngestionDispatcher implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionDispatcher.class);

    private final KnowledgeIngestionJobRepository ingestionJobRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeProperties properties;
    private final KnowledgeIngestionRunner runner;
    private final Executor executor;
    private final ThreadPoolTaskScheduler scheduler;
    private final Semaphore workerSlots;
    private final String workerId = "worker_" + UuidUtil.newUuid();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledFuture<?> dispatchFuture;

    public KnowledgeIngestionDispatcher(KnowledgeIngestionJobRepository ingestionJobRepository,
                                        KnowledgeDocumentRepository documentRepository,
                                        KnowledgeProperties properties, KnowledgeIngestionRunner runner,
                                        @Qualifier("knowledgeIngestionExecutor") Executor executor,
                                        @Qualifier("knowledgeIngestionScheduler") ThreadPoolTaskScheduler scheduler) {
        this.ingestionJobRepository = ingestionJobRepository;
        this.documentRepository = documentRepository;
        this.properties = properties;
        this.runner = runner;
        this.executor = executor;
        this.scheduler = scheduler;
        workerSlots = new Semaphore(Math.max(1, properties.getIngestion().getWorkerCount()));
    }

    /**
     * Starts polling only after application runners, including Flyway bootstrap, have completed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        start();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        dispatchFuture = scheduler.scheduleWithFixedDelay(this::dispatchSafely,
                properties.getIngestion().getDispatchInterval());
        log.info("[KnowledgeIngestion] dispatcher started workerId={}", workerId);
    }

    public void stop() {
        running.set(false);
        if (dispatchFuture != null) dispatchFuture.cancel(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void destroy() {
        stop();
    }

    private void dispatchSafely() {
        if (!running.get()) return;
        try {
            expireExhaustedJobs();
            dispatchAvailableJobs();
        } catch (Exception ex) {
            log.warn("[KnowledgeIngestion] dispatch cycle failed workerId={}", workerId, ex);
        }
    }

    private void dispatchAvailableJobs() {
        int available = workerSlots.availablePermits();
        if (available <= 0) return;
        LocalDateTime now = LocalDateTime.now();
        List<KnowledgeIngestionJobEntity> candidates = ingestionJobRepository.listRunnableCandidates(
                now, properties.getIngestion().getMaxAttempts(), available);
        for (KnowledgeIngestionJobEntity candidate : candidates) {
            if (!workerSlots.tryAcquire()) break;
            claimAndSubmit(candidate, now);
        }
    }

    private void claimAndSubmit(KnowledgeIngestionJobEntity candidate, LocalDateTime now) {
        String token = UuidUtil.newUuid();
        LocalDateTime leaseUntil = now.plusSeconds(Math.max(1, properties.getIngestion().getLeaseSeconds()));
        int nextAttempt = (candidate.getAttemptCount() == null ? 0 : candidate.getAttemptCount()) + 1;
        if (!ingestionJobRepository.claimRunnable(candidate, workerId, token, now, leaseUntil)) {
            workerSlots.release();
            return;
        }
        try {
            executor.execute(() -> executeClaimed(candidate.getJobUid(), token));
            log.info("[KnowledgeIngestion] claimed jobUid={} workerId={} attempt={}",
                    candidate.getJobUid(), workerId, nextAttempt);
        } catch (RejectedExecutionException ex) {
            releaseRejected(candidate.getJobUid(), token);
            workerSlots.release();
            log.warn("[KnowledgeIngestion] executor rejected jobUid={}", candidate.getJobUid());
        }
    }

    private void executeClaimed(String jobUid, String token) {
        Duration heartbeatInterval = Duration.ofSeconds(Math.max(1, properties.getIngestion().getLeaseSeconds() / 3));
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> renewLease(jobUid, token), heartbeatInterval);
        try {
            runner.runIngestion(jobUid, token);
        } finally {
            heartbeat.cancel(false);
            workerSlots.release();
        }
    }

    private void renewLease(String jobUid, String token) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeIngestionJobEntity job = ingestionJobRepository.findRunningByUidAndToken(jobUid, token);
        if (job == null) {
            log.debug("[KnowledgeIngestion] heartbeat ignored after lease loss jobUid={}", jobUid);
            return;
        }
        job.setLeaseUntil(toDate(now.plusSeconds(Math.max(1, properties.getIngestion().getLeaseSeconds()))));
        job.setLastHeartbeatTime(toDate(now));
        job.setUpdatedTime(toDate(now));
        ingestionJobRepository.updateById(job);
    }

    private void releaseRejected(String jobUid, String token) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeIngestionJobEntity job = ingestionJobRepository.findRunningByUidAndToken(jobUid, token);
        if (job == null) return;
        job.setStatus("PENDING");
        job.setStage("QUEUED");
        job.setWorkerId(null);
        job.setLeaseToken(null);
        job.setLeaseUntil(null);
        job.setLastHeartbeatTime(null);
        job.setAttemptCount(Math.max(0, job.getAttemptCount() == null ? 0 : job.getAttemptCount() - 1));
        job.setUpdatedTime(toDate(now));
        ingestionJobRepository.updateById(job);
    }

    private void expireExhaustedJobs() {
        LocalDateTime now = LocalDateTime.now();
        for (KnowledgeIngestionJobEntity job : ingestionJobRepository.listExhaustedRunning(
                now, properties.getIngestion().getMaxAttempts())) {
            job.setStatus("FAILED");
            job.setRetryable(true);
            job.setFailureCode("LEASE_EXPIRED");
            job.setFailureMessage("任务租约过期且已达到最大重试次数");
            job.setWorkerId(null);
            job.setLeaseToken(null);
            job.setLeaseUntil(null);
            job.setFinishedTime(toDate(now));
            job.setUpdatedTime(toDate(now));
            if (ingestionJobRepository.updateById(job)) {
                KnowledgeDocumentEntity document = documentRepository.findByUid(job.getDocumentUid());
                if (document != null) {
                    document.setStatus("FAILED");
                    document.setFailureCode("LEASE_EXPIRED");
                    document.setFailureMessage("任务租约过期且已达到最大重试次数");
                    document.setUpdatedTime(toDate(now));
                    documentRepository.updateById(document);
                }
            }
        }
    }

    private java.util.Date toDate(LocalDateTime value) {
        return java.sql.Timestamp.valueOf(value);
    }
}
