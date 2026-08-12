package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.TokenUsageRecordEntity;
import ai.nomoclaw.bot.store.mapper.TokenUsageRecordMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TokenUsageRecordRepository extends CrudRepository<TokenUsageRecordMapper, TokenUsageRecordEntity> {

    public void deleteByConversationUid(String conversationUid) {
        lambdaUpdate().eq(TokenUsageRecordEntity::getConversationUid, conversationUid).remove();
    }

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
                .eq(StringUtils.hasText(provider), TokenUsageRecordEntity::getProvider, StringUtils.trimWhitespace(provider))
                .eq(StringUtils.hasText(modelName), TokenUsageRecordEntity::getModelName, StringUtils.trimWhitespace(modelName))
                .eq(StringUtils.hasText(scene), TokenUsageRecordEntity::getScene, StringUtils.trimWhitespace(scene))
                .orderByDesc(TokenUsageRecordEntity::getOccurredTime)
                .list();
    }
}
