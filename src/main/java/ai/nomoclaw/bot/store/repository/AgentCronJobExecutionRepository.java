package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.AgentCronJobExecutionEntity;
import ai.nomoclaw.bot.store.mapper.AgentCronJobExecutionMapper;
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
                .orderByDesc(AgentCronJobExecutionEntity::getStartedTime, AgentCronJobExecutionEntity::getId)
                .last("LIMIT " + Math.max(limit, 1))
                .list();
    }

    public AgentCronJobExecutionEntity findLatestRunningByJobUid(String jobUid) {
        return lambdaQuery()
                .eq(AgentCronJobExecutionEntity::getJobUid, jobUid)
                .eq(AgentCronJobExecutionEntity::getStatus, "RUNNING")
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
