package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.AgentCronJobExecutionEntity;
import ai.nomoclaw.bot.store.mapper.AgentCronJobExecutionMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgentCronJobExecutionRepository extends CrudRepository<AgentCronJobExecutionMapper, AgentCronJobExecutionEntity> {

    public AgentCronJobExecutionEntity findByExecutionUid(String executionUid) {
        return lambdaQuery()
                .eq(AgentCronJobExecutionEntity::getExecutionUid, executionUid)
                .last("LIMIT 1")
                .one();
    }

    public List<AgentCronJobExecutionEntity> listByJobUid(String jobUid, int limit) {
        return lambdaQuery()
                .eq(AgentCronJobExecutionEntity::getJobUid, jobUid)
                .orderByDesc(AgentCronJobExecutionEntity::getStartedTime, AgentCronJobExecutionEntity::getId)
                .last("LIMIT " + Math.max(limit, 1))
                .list();
    }

    public List<AgentCronJobExecutionEntity> listRecent(int limit) {
        return lambdaQuery()
                .in(AgentCronJobExecutionEntity::getStatus, List.of("COMPLETED", "FAILED", "CANCELED"))
                .orderByDesc(AgentCronJobExecutionEntity::getStartedTime, AgentCronJobExecutionEntity::getId)
                .last("LIMIT " + Math.max(limit, 1))
                .list();
    }

    public List<AgentCronJobExecutionEntity> listRunning(int limit) {
        return lambdaQuery()
                .in(AgentCronJobExecutionEntity::getStatus, List.of("RUNNING", "WAITING_APPROVAL"))
                .orderByDesc(AgentCronJobExecutionEntity::getStartedTime, AgentCronJobExecutionEntity::getId)
                .last("LIMIT " + Math.max(limit, 1))
                .list();
    }

    public IPage<AgentCronJobExecutionEntity> pageHistory(String agentUid,
                                                          String status,
                                                          LocalDateTime startTime,
                                                          LocalDateTime endTime,
                                                          int page,
                                                          int pageSize) {
        var query = lambdaQuery();
        if (agentUid != null && !agentUid.isBlank()) {
            query = query.eq(AgentCronJobExecutionEntity::getAgentUid, agentUid.trim());
        }
        if (status != null && !status.isBlank()) {
            query = query.eq(AgentCronJobExecutionEntity::getStatus, status.trim().toUpperCase());
        } else {
            query = query.in(AgentCronJobExecutionEntity::getStatus, List.of("COMPLETED", "FAILED", "CANCELED"));
        }
        if (startTime != null) {
            query = query.ge(AgentCronJobExecutionEntity::getStartedTime, startTime);
        }
        if (endTime != null) {
            query = query.le(AgentCronJobExecutionEntity::getStartedTime, endTime);
        }
        return query
                .orderByDesc(AgentCronJobExecutionEntity::getStartedTime, AgentCronJobExecutionEntity::getId)
                .page(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)));
    }

    public AgentCronJobExecutionEntity findLatestRunningByJobUid(String jobUid) {
        return lambdaQuery()
                .eq(AgentCronJobExecutionEntity::getJobUid, jobUid)
                .in(AgentCronJobExecutionEntity::getStatus, List.of("RUNNING", "WAITING_APPROVAL"))
                .orderByDesc(AgentCronJobExecutionEntity::getStartedTime, AgentCronJobExecutionEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public AgentCronJobExecutionEntity findLatestFinishedByJobUid(String jobUid) {
        return lambdaQuery()
                .eq(AgentCronJobExecutionEntity::getJobUid, jobUid)
                .in(AgentCronJobExecutionEntity::getStatus, List.of("COMPLETED", "FAILED", "CANCELED"))
                .orderByDesc(AgentCronJobExecutionEntity::getFinishedTime, AgentCronJobExecutionEntity::getStartedTime, AgentCronJobExecutionEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public void markRead(String executionUid, LocalDateTime now) {
        lambdaUpdate()
                .eq(AgentCronJobExecutionEntity::getExecutionUid, executionUid)
                .set(AgentCronJobExecutionEntity::getReadFlag, 1)
                .set(AgentCronJobExecutionEntity::getUpdatedTime, now)
                .update();
    }
}
