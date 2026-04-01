package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.ToolDefinitionEntity;
import ai.nomoclaw.bot.store.mapper.ToolDefinitionMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class ToolDefinitionRepository extends CrudRepository<ToolDefinitionMapper, ToolDefinitionEntity> {

    public ToolDefinitionEntity findActiveByKey(String toolKey) {
        if (toolKey == null || toolKey.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(ToolDefinitionEntity::getToolKey, toolKey)
                .eq(ToolDefinitionEntity::getStatus, "ACTIVE")
                .one();
    }

    public List<ToolDefinitionEntity> listAllActive() {
        return lambdaQuery()
                .eq(ToolDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(ToolDefinitionEntity::getSortIndex, ToolDefinitionEntity::getDisplayName)
                .list();
    }

    public List<ToolDefinitionEntity> listActiveByKeys(Collection<String> toolKeys) {
        if (toolKeys == null || toolKeys.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(ToolDefinitionEntity::getToolKey, toolKeys)
                .eq(ToolDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(ToolDefinitionEntity::getSortIndex, ToolDefinitionEntity::getDisplayName)
                .list();
    }
}
