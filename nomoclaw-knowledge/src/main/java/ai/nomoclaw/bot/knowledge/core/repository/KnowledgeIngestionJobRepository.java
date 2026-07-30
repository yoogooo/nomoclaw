package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeIngestionJobEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeIngestionJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Repository
public class KnowledgeIngestionJobRepository extends CrudRepository<KnowledgeIngestionJobMapper, KnowledgeIngestionJobEntity> {

    /** Finds the latest persisted ingestion job for one document. */
    public KnowledgeIngestionJobEntity findLatestByDocument(String documentUid) {
        return lambdaQuery().eq(KnowledgeIngestionJobEntity::getDocumentUid, documentUid)
                .orderByDesc(KnowledgeIngestionJobEntity::getId).last("LIMIT 1").one();
    }

    /** Finds one ingestion job by business UID. */
    public KnowledgeIngestionJobEntity findByUid(String jobUid) {
        return lambdaQuery().eq(KnowledgeIngestionJobEntity::getJobUid, jobUid).one();
    }

    /** Counts jobs persisted for one document. */
    public long countByDocument(String documentUid) {
        return lambdaQuery().eq(KnowledgeIngestionJobEntity::getDocumentUid, documentUid).count();
    }

    /** Lists all jobs for a group of documents. */
    public List<KnowledgeIngestionJobEntity> listByDocuments(List<String> documentUids) {
        if (documentUids == null || documentUids.isEmpty()) return List.of();
        return lambdaQuery().in(KnowledgeIngestionJobEntity::getDocumentUid, documentUids).list();
    }

    /** Lists jobs that are ready to be claimed, ordered by persistence ID. */
    public List<KnowledgeIngestionJobEntity> listRunnableCandidates(LocalDateTime now, int maxAttempts, int limit) {
        if (limit <= 0) return List.of();
        LambdaQueryWrapper<KnowledgeIngestionJobEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(group -> group
                        .and(ready -> ready
                                .in(KnowledgeIngestionJobEntity::getStatus, List.of("PENDING", "RETRY_WAIT"))
                                .and(nextRetry -> nextRetry.isNull(KnowledgeIngestionJobEntity::getNextRetryTime)
                                        .or()
                                        .le(KnowledgeIngestionJobEntity::getNextRetryTime, toDate(now))))
                        .or(running -> running
                                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                                .lt(KnowledgeIngestionJobEntity::getLeaseUntil, toDate(now))))
                .lt(KnowledgeIngestionJobEntity::getAttemptCount, maxAttempts)
                .orderByAsc(KnowledgeIngestionJobEntity::getId)
                .last("LIMIT " + limit);
        return list(wrapper);
    }

    /** Finds a running job protected by the current lease token. */
    public KnowledgeIngestionJobEntity findRunningByUidAndToken(String jobUid, String leaseToken) {
        return lambdaQuery().eq(KnowledgeIngestionJobEntity::getJobUid, jobUid)
                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .eq(KnowledgeIngestionJobEntity::getLeaseToken, leaseToken)
                .one();
    }

    /** Lists exhausted jobs whose lease has expired after reaching the retry ceiling. */
    public List<KnowledgeIngestionJobEntity> listExhaustedRunning(LocalDateTime now, int maxAttempts) {
        return lambdaQuery().eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .lt(KnowledgeIngestionJobEntity::getLeaseUntil, toDate(now))
                .ge(KnowledgeIngestionJobEntity::getAttemptCount, maxAttempts)
                .list();
    }

    /** Deletes all ingestion jobs that belong to one document. */
    public void deleteByDocument(String documentUid) {
        lambdaUpdate().eq(KnowledgeIngestionJobEntity::getDocumentUid, documentUid).remove();
    }

    /** Resets a failed job so it can be claimed again. */
    public void resetForRetry(String jobUid, LocalDateTime updatedTime) {
        lambdaUpdate().eq(KnowledgeIngestionJobEntity::getJobUid, jobUid)
                .set(KnowledgeIngestionJobEntity::getStatus, "PENDING")
                .set(KnowledgeIngestionJobEntity::getStage, "QUEUED")
                .set(KnowledgeIngestionJobEntity::getProgressPercent, 0)
                .set(KnowledgeIngestionJobEntity::getTotalChunks, 0)
                .set(KnowledgeIngestionJobEntity::getProcessedChunks, 0)
                .set(KnowledgeIngestionJobEntity::getProcessedPages, 0)
                .set(KnowledgeIngestionJobEntity::getTotalPages, 0)
                .set(KnowledgeIngestionJobEntity::getCacheHitChunks, 0)
                .set(KnowledgeIngestionJobEntity::getCacheMissChunks, 0)
                .set(KnowledgeIngestionJobEntity::getAttemptCount, 0)
                .set(KnowledgeIngestionJobEntity::getFailureCode, "")
                .set(KnowledgeIngestionJobEntity::getFailureMessage, "")
                .set(KnowledgeIngestionJobEntity::getWorkerId, null)
                .set(KnowledgeIngestionJobEntity::getLeaseToken, null)
                .set(KnowledgeIngestionJobEntity::getLeaseUntil, null)
                .set(KnowledgeIngestionJobEntity::getLastHeartbeatTime, null)
                .set(KnowledgeIngestionJobEntity::getNextRetryTime, null)
                .set(KnowledgeIngestionJobEntity::getRetryable, false)
                .set(KnowledgeIngestionJobEntity::getStartedTime, null)
                .set(KnowledgeIngestionJobEntity::getFinishedTime, null)
                .set(KnowledgeIngestionJobEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Returns whether a job still holds its running lease. */
    public boolean hasRunningLease(String jobUid, String leaseToken) {
        return findRunningByUidAndToken(jobUid, leaseToken) != null;
    }

    /** Atomically claims a runnable candidate if it still matches the scheduler predicate. */
    public boolean claimRunnable(KnowledgeIngestionJobEntity candidate, String workerId, String leaseToken,
                                 LocalDateTime now, LocalDateTime leaseUntil) {
        return lambdaUpdate().eq(KnowledgeIngestionJobEntity::getId, candidate.getId())
                .eq(KnowledgeIngestionJobEntity::getAttemptCount, candidate.getAttemptCount())
                .and(group -> group
                        .and(ready -> ready
                                .in(KnowledgeIngestionJobEntity::getStatus, List.of("PENDING", "RETRY_WAIT"))
                                .and(nextRetry -> nextRetry.isNull(KnowledgeIngestionJobEntity::getNextRetryTime)
                                        .or()
                                        .le(KnowledgeIngestionJobEntity::getNextRetryTime, toDate(now))))
                        .or(running -> running
                                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                                .lt(KnowledgeIngestionJobEntity::getLeaseUntil, toDate(now))))
                .set(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .set(KnowledgeIngestionJobEntity::getStage, "QUEUED")
                .set(KnowledgeIngestionJobEntity::getWorkerId, workerId)
                .set(KnowledgeIngestionJobEntity::getLeaseToken, leaseToken)
                .set(KnowledgeIngestionJobEntity::getLeaseUntil, toDate(leaseUntil))
                .set(KnowledgeIngestionJobEntity::getLastHeartbeatTime, toDate(now))
                .set(KnowledgeIngestionJobEntity::getNextRetryTime, null)
                .set(KnowledgeIngestionJobEntity::getRetryable, false)
                .set(KnowledgeIngestionJobEntity::getAttemptCount, zero(candidate.getAttemptCount()) + 1)
                .set(candidate.getStartedTime() == null, KnowledgeIngestionJobEntity::getStartedTime, toDate(now))
                .set(KnowledgeIngestionJobEntity::getFinishedTime, null)
                .set(KnowledgeIngestionJobEntity::getUpdatedTime, toDate(now))
                .update();
    }

    /** Updates the high-level stage for a running leased job. */
    public boolean updateRunningStage(String jobUid, String leaseToken, String stage, int progress,
                                      LocalDateTime updatedTime) {
        return lambdaUpdate().eq(KnowledgeIngestionJobEntity::getJobUid, jobUid)
                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .eq(KnowledgeIngestionJobEntity::getLeaseToken, leaseToken)
                .set(KnowledgeIngestionJobEntity::getStage, stage)
                .set(KnowledgeIngestionJobEntity::getProgressPercent, progress)
                .set(KnowledgeIngestionJobEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Updates chunk processing progress for a running leased job. */
    public boolean updateRunningChunkProgress(String jobUid, String leaseToken, String stage, int progress,
                                              int processed, int total, LocalDateTime updatedTime) {
        return lambdaUpdate().eq(KnowledgeIngestionJobEntity::getJobUid, jobUid)
                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .eq(KnowledgeIngestionJobEntity::getLeaseToken, leaseToken)
                .set(KnowledgeIngestionJobEntity::getStage, stage)
                .set(KnowledgeIngestionJobEntity::getProgressPercent, progress)
                .set(KnowledgeIngestionJobEntity::getProcessedChunks, processed)
                .set(KnowledgeIngestionJobEntity::getTotalChunks, total)
                .set(KnowledgeIngestionJobEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Updates page parsing progress for a running leased job. */
    public boolean updateRunningPageProgress(String jobUid, String leaseToken, int processedPages,
                                             int totalPages, LocalDateTime updatedTime) {
        return lambdaUpdate().eq(KnowledgeIngestionJobEntity::getJobUid, jobUid)
                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .eq(KnowledgeIngestionJobEntity::getLeaseToken, leaseToken)
                .set(KnowledgeIngestionJobEntity::getProcessedPages, processedPages)
                .set(KnowledgeIngestionJobEntity::getTotalPages, totalPages)
                .set(KnowledgeIngestionJobEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    /** Marks a running leased job completed and releases the lease. */
    public boolean completeRunning(String jobUid, String leaseToken, int chunkCount, LocalDateTime finishedTime) {
        return lambdaUpdate().eq(KnowledgeIngestionJobEntity::getJobUid, jobUid)
                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .eq(KnowledgeIngestionJobEntity::getLeaseToken, leaseToken)
                .set(KnowledgeIngestionJobEntity::getStatus, "COMPLETED")
                .set(KnowledgeIngestionJobEntity::getStage, "PUBLISHING")
                .set(KnowledgeIngestionJobEntity::getProgressPercent, 100)
                .set(KnowledgeIngestionJobEntity::getProcessedChunks, chunkCount)
                .set(KnowledgeIngestionJobEntity::getTotalChunks, chunkCount)
                .set(KnowledgeIngestionJobEntity::getRetryable, false)
                .set(KnowledgeIngestionJobEntity::getWorkerId, null)
                .set(KnowledgeIngestionJobEntity::getLeaseToken, null)
                .set(KnowledgeIngestionJobEntity::getLeaseUntil, null)
                .set(KnowledgeIngestionJobEntity::getLastHeartbeatTime, null)
                .set(KnowledgeIngestionJobEntity::getNextRetryTime, null)
                .set(KnowledgeIngestionJobEntity::getFinishedTime, toDate(finishedTime))
                .set(KnowledgeIngestionJobEntity::getUpdatedTime, toDate(finishedTime))
                .update();
    }

    /** Adds embedding cache hit/miss counters for a running leased job. */
    public boolean addCacheStats(String jobUid, String leaseToken, int hits, int misses,
                                 LocalDateTime updatedTime) {
        KnowledgeIngestionJobEntity job = findRunningByUidAndToken(jobUid, leaseToken);
        if (job == null) return false;
        job.setCacheHitChunks(zero(job.getCacheHitChunks()) + hits);
        job.setCacheMissChunks(zero(job.getCacheMissChunks()) + misses);
        job.setUpdatedTime(toDate(updatedTime));
        return updateById(job);
    }

    /** Applies a retry or terminal failure to a running leased job. */
    public boolean failRunning(String jobUid, String leaseToken, String status, String stage, String failureCode,
                               String failureMessage, boolean retryable, LocalDateTime nextRetryTime,
                               LocalDateTime finishedTime, LocalDateTime updatedTime) {
        return lambdaUpdate().eq(KnowledgeIngestionJobEntity::getJobUid, jobUid)
                .eq(KnowledgeIngestionJobEntity::getStatus, "RUNNING")
                .eq(KnowledgeIngestionJobEntity::getLeaseToken, leaseToken)
                .set(KnowledgeIngestionJobEntity::getStatus, status)
                .set(KnowledgeIngestionJobEntity::getStage, stage)
                .set(KnowledgeIngestionJobEntity::getFailureCode, failureCode)
                .set(KnowledgeIngestionJobEntity::getFailureMessage, failureMessage)
                .set(KnowledgeIngestionJobEntity::getRetryable, retryable)
                .set(KnowledgeIngestionJobEntity::getNextRetryTime, nextRetryTime == null ? null : toDate(nextRetryTime))
                .set(KnowledgeIngestionJobEntity::getWorkerId, null)
                .set(KnowledgeIngestionJobEntity::getLeaseToken, null)
                .set(KnowledgeIngestionJobEntity::getLeaseUntil, null)
                .set(KnowledgeIngestionJobEntity::getLastHeartbeatTime, null)
                .set(KnowledgeIngestionJobEntity::getFinishedTime, finishedTime == null ? null : toDate(finishedTime))
                .set(KnowledgeIngestionJobEntity::getUpdatedTime, toDate(updatedTime))
                .update();
    }

    private int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
