package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.LlmProviderModelEntity;
import ai.nomoclaw.bot.store.mapper.LlmProviderModelMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class LlmProviderModelRepository extends CrudRepository<LlmProviderModelMapper, LlmProviderModelEntity> {

    public List<LlmProviderModelEntity> listByProviderIds(Collection<String> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(LlmProviderModelEntity::getProviderId, providerIds)
                .eq(LlmProviderModelEntity::getStatus, "ACTIVE")
                .orderByAsc(LlmProviderModelEntity::getProviderId, LlmProviderModelEntity::getSortIndex)
                .list();
    }

    public void deleteByProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return;
        }
        lambdaUpdate()
                .eq(LlmProviderModelEntity::getProviderId, providerId)
                .remove();
    }
}
