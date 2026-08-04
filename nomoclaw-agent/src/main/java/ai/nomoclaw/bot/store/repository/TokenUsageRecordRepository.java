package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.TokenUsageRecordEntity;
import ai.nomoclaw.bot.store.mapper.TokenUsageRecordMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TokenUsageRecordRepository extends CrudRepository<TokenUsageRecordMapper, TokenUsageRecordEntity> {

    /**
     * Returns usage records matching the optional dashboard filters.
     */
    public List<TokenUsageRecordEntity> listByFilter(LocalDateTime from,
                                                      LocalDateTime to,
                                                      String provider,
                                                      String modelName,
                                                      String scene) {
        return lambdaQuery()
                .ge(from != null, TokenUsageRecordEntity::getOccurredTime, from)
                .le(to != null, TokenUsageRecordEntity::getOccurredTime, to)
                .eq(hasText(provider), TokenUsageRecordEntity::getProvider, normalize(provider))
                .eq(hasText(modelName), TokenUsageRecordEntity::getModelName, normalize(modelName))
                .eq(hasText(scene), TokenUsageRecordEntity::getScene, normalize(scene))
                .orderByDesc(TokenUsageRecordEntity::getOccurredTime)
                .list();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
