package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.mapper.AgentCronJobMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentCronJobRepository extends CrudRepository<AgentCronJobMapper, AgentCronJobEntity> {

    public List<AgentCronJobEntity> listActive() {
        return lambdaQuery()
                .eq(AgentCronJobEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentCronJobEntity::getCreatedTime, AgentCronJobEntity::getId)
                .list();
    }

    public List<AgentCronJobEntity> listAllJobs() {
        return lambdaQuery()
                .orderByDesc(AgentCronJobEntity::getUpdatedTime, AgentCronJobEntity::getCreatedTime, AgentCronJobEntity::getId)
                .list();
    }

    public AgentCronJobEntity findByJobUid(String jobUid) {
        return lambdaQuery()
                .eq(AgentCronJobEntity::getJobUid, jobUid)
                .last("LIMIT 1")
                .one();
    }
}
