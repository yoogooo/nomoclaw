package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.AgentCronSubscriptionEntity;
import ai.nomoclaw.bot.store.mapper.AgentCronSubscriptionMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentCronSubscriptionRepository extends CrudRepository<AgentCronSubscriptionMapper, AgentCronSubscriptionEntity> {

    public List<AgentCronSubscriptionEntity> listByJobUid(String jobUid) {
        return lambdaQuery()
                .eq(AgentCronSubscriptionEntity::getJobUid, jobUid)
                .orderByAsc(AgentCronSubscriptionEntity::getId)
                .list();
    }

    public List<AgentCronSubscriptionEntity> listEnabledByJobUid(String jobUid) {
        return lambdaQuery()
                .eq(AgentCronSubscriptionEntity::getJobUid, jobUid)
                .eq(AgentCronSubscriptionEntity::getEnabled, 1)
                .orderByAsc(AgentCronSubscriptionEntity::getId)
                .list();
    }

    public void deleteByJobUid(String jobUid) {
        lambdaUpdate()
                .eq(AgentCronSubscriptionEntity::getJobUid, jobUid)
                .remove();
    }
}
