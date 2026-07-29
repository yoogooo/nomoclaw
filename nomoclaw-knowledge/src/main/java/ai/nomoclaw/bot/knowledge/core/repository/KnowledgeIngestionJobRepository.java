package ai.nomoclaw.bot.knowledge.core.repository;

import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeIngestionJobEntity;
import ai.nomoclaw.bot.knowledge.core.mapper.KnowledgeIngestionJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    private Date toDate(LocalDateTime value) {
        return java.sql.Timestamp.valueOf(value);
    }

    /** Deletes all ingestion jobs that belong to one document. */
    public void deleteByDocument(String documentUid) {
        lambdaUpdate().eq(KnowledgeIngestionJobEntity::getDocumentUid, documentUid).remove();
    }
}
