package ai.nomoclaw.bot.knowledge.app;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgePersistenceRepository;
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

    private final KnowledgePersistenceRepository persistence;
    private final KnowledgeProperties properties;
    private final KnowledgeIngestionRunner runner;
    private final Executor executor;
    private final ThreadPoolTaskScheduler scheduler;
    private final Semaphore workerSlots;
    private final String workerId = "worker_" + UuidUtil.newUuid();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledFuture<?> dispatchFuture;

    public KnowledgeIngestionDispatcher(KnowledgePersistenceRepository persistence, KnowledgeProperties properties, KnowledgeIngestionRunner runner,
                                        @Qualifier("knowledgeIngestionExecutor") Executor executor,
                                        @Qualifier("knowledgeIngestionScheduler") ThreadPoolTaskScheduler scheduler) {
        this.persistence = persistence;
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
        List<JobCandidate> candidates = persistence.query("SELECT id,job_uid,status,attempt_count FROM knowledge_ingestion_job "
                        + "WHERE (((status='PENDING' OR status='RETRY_WAIT') AND (next_retry_time IS NULL OR next_retry_time<=?)) "
                        + "OR (status='RUNNING' AND lease_until<?)) AND attempt_count<? ORDER BY id LIMIT ?",
                (rs, row) -> new JobCandidate(rs.getLong("id"), rs.getString("job_uid"),
                        rs.getString("status"), rs.getInt("attempt_count")),
                now, now, properties.getIngestion().getMaxAttempts(), available);
        for (JobCandidate candidate : candidates) {
            if (!workerSlots.tryAcquire()) break;
            claimAndSubmit(candidate, now);
        }
    }

    private void claimAndSubmit(JobCandidate candidate, LocalDateTime now) {
        String token = UuidUtil.newUuid();
        LocalDateTime leaseUntil = now.plusSeconds(Math.max(1, properties.getIngestion().getLeaseSeconds()));
        int updated = persistence.update("UPDATE knowledge_ingestion_job SET status='RUNNING',stage='QUEUED',worker_id=?,lease_token=?,"
                        + "lease_until=?,last_heartbeat_time=?,next_retry_time=NULL,retryable=0,attempt_count=attempt_count+1,"
                        + "started_time=COALESCE(started_time,?),finished_time=NULL,updated_time=? WHERE id=? AND attempt_count=? "
                        + "AND (((status='PENDING' OR status='RETRY_WAIT') AND (next_retry_time IS NULL OR next_retry_time<=?)) "
                        + "OR (status='RUNNING' AND lease_until<?))",
                workerId, token, leaseUntil, now, now, now, candidate.id(), candidate.attemptCount(), now, now);
        if (updated == 0) {
            workerSlots.release();
            return;
        }
        try {
            executor.execute(() -> executeClaimed(candidate.jobUid(), token));
            log.info("[KnowledgeIngestion] claimed jobUid={} workerId={} attempt={}",
                    candidate.jobUid(), workerId, candidate.attemptCount() + 1);
        } catch (RejectedExecutionException ex) {
            releaseRejected(candidate.jobUid(), token);
            workerSlots.release();
            log.warn("[KnowledgeIngestion] executor rejected jobUid={}", candidate.jobUid());
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
        int updated = persistence.update("UPDATE knowledge_ingestion_job SET lease_until=?,last_heartbeat_time=?,updated_time=? "
                        + "WHERE job_uid=? AND status='RUNNING' AND lease_token=?",
                now.plusSeconds(Math.max(1, properties.getIngestion().getLeaseSeconds())), now, now, jobUid, token);
        if (updated == 0) log.debug("[KnowledgeIngestion] heartbeat ignored after lease loss jobUid={}", jobUid);
    }

    private void releaseRejected(String jobUid, String token) {
        LocalDateTime now = LocalDateTime.now();
        persistence.update("UPDATE knowledge_ingestion_job SET status='PENDING',stage='QUEUED',worker_id=NULL,lease_token=NULL,"
                        + "lease_until=NULL,last_heartbeat_time=NULL,attempt_count=CASE WHEN attempt_count>0 THEN attempt_count-1 ELSE 0 END,updated_time=? "
                        + "WHERE job_uid=? AND status='RUNNING' AND lease_token=?",
                now, jobUid, token);
    }

    private void expireExhaustedJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<String> exhausted = persistence.queryForList("SELECT job_uid FROM knowledge_ingestion_job WHERE status='RUNNING' "
                        + "AND lease_until<? AND attempt_count>=?", String.class,
                now, properties.getIngestion().getMaxAttempts());
        for (String jobUid : exhausted) {
            int updated = persistence.update("UPDATE knowledge_ingestion_job SET status='FAILED',retryable=1,"
                            + "failure_code='LEASE_EXPIRED',failure_message='任务租约过期且已达到最大重试次数',worker_id=NULL,"
                            + "lease_token=NULL,lease_until=NULL,finished_time=?,updated_time=? WHERE job_uid=? AND status='RUNNING' "
                            + "AND lease_until<? AND attempt_count>=?",
                    now, now, jobUid, now, properties.getIngestion().getMaxAttempts());
            if (updated > 0) {
                persistence.update("UPDATE knowledge_document SET status='FAILED',failure_code='LEASE_EXPIRED',"
                                + "failure_message='任务租约过期且已达到最大重试次数',updated_time=? WHERE document_uid="
                                + "(SELECT document_uid FROM knowledge_ingestion_job WHERE job_uid=?)",
                        now, jobUid);
            }
        }
    }

    private record JobCandidate(long id, String jobUid, String status, int attemptCount) {
    }
}
