package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.LlmProviderConfigEntity;
import ai.nomoclaw.bot.store.mapper.LlmProviderConfigMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class LlmProviderConfigRepository extends CrudRepository<LlmProviderConfigMapper, LlmProviderConfigEntity> {

    public List<LlmProviderConfigEntity> listAll() {
        return lambdaQuery()
                .orderByAsc(LlmProviderConfigEntity::getId, LlmProviderConfigEntity::getProviderName)
                .list();
    }

    public List<LlmProviderConfigEntity> listByProviderIds(Collection<String> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(LlmProviderConfigEntity::getProviderId, providerIds)
                .list();
    }

    public LlmProviderConfigEntity findByProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(LlmProviderConfigEntity::getProviderId, providerId)
                .last("LIMIT 1")
                .one();
    }
}
