package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.SystemErrorLogEntity;
import ai.nomoclaw.bot.store.mapper.SystemErrorLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SystemErrorLogRepository extends CrudRepository<SystemErrorLogMapper, SystemErrorLogEntity> {

    public List<SystemErrorLogEntity> listLatest(int limit, String keyword) {
        int normalizedLimit = Math.max(limit, 1);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return lambdaQuery()
                .and(StringUtils.hasText(normalizedKeyword), wrapper -> wrapper
                        .like(SystemErrorLogEntity::getTitle, normalizedKeyword)
                        .or()
                        .like(SystemErrorLogEntity::getMessage, normalizedKeyword)
                        .or()
                        .like(SystemErrorLogEntity::getDetail, normalizedKeyword)
                        .or()
                        .like(SystemErrorLogEntity::getSource, normalizedKeyword)
                        .or()
                        .like(SystemErrorLogEntity::getCode, normalizedKeyword))
                .orderByDesc(SystemErrorLogEntity::getOccurredTime, SystemErrorLogEntity::getId)
                .last("LIMIT " + normalizedLimit)
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
