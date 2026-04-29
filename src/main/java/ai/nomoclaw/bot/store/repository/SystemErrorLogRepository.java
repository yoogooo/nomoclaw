package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.SystemErrorLogEntity;
import ai.nomoclaw.bot.store.mapper.SystemErrorLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SystemErrorLogRepository extends CrudRepository<SystemErrorLogMapper, SystemErrorLogEntity> {

    public List<SystemErrorLogEntity> listLatest(int limit) {
        return lambdaQuery()
                .orderByDesc(SystemErrorLogEntity::getOccurredTime, SystemErrorLogEntity::getId)
                .last("LIMIT " + Math.max(limit, 1))
                .list();
    }

    public SystemErrorLogEntity latest() {
        return lambdaQuery()
                .orderByDesc(SystemErrorLogEntity::getOccurredTime, SystemErrorLogEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public long countSince(LocalDateTime since) {
        return lambdaQuery()
                .ge(SystemErrorLogEntity::getOccurredTime, since)
                .count();
    }

    public void trimToLatest(int keepCount) {
        List<SystemErrorLogEntity> stale = lambdaQuery()
                .select(SystemErrorLogEntity::getId)
                .orderByDesc(SystemErrorLogEntity::getOccurredTime, SystemErrorLogEntity::getId)
                .last("LIMIT 100000 OFFSET " + Math.max(keepCount, 0))
                .list();
        if (stale.isEmpty()) {
            return;
        }
        List<Long> staleIds = stale.stream().map(SystemErrorLogEntity::getId).toList();
        remove(new LambdaQueryWrapper<SystemErrorLogEntity>().in(SystemErrorLogEntity::getId, staleIds));
    }
}
