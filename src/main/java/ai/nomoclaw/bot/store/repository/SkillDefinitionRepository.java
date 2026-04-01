package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.SkillDefinitionEntity;
import ai.nomoclaw.bot.store.mapper.SkillDefinitionMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class SkillDefinitionRepository extends CrudRepository<SkillDefinitionMapper, SkillDefinitionEntity> {

    public SkillDefinitionEntity findActiveByKey(String skillKey) {
        if (skillKey == null || skillKey.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(SkillDefinitionEntity::getSkillKey, skillKey)
                .eq(SkillDefinitionEntity::getStatus, "ACTIVE")
                .one();
    }

    public SkillDefinitionEntity findByKey(String skillKey) {
        if (skillKey == null || skillKey.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(SkillDefinitionEntity::getSkillKey, skillKey)
                .one();
    }

    public List<SkillDefinitionEntity> listAllActive() {
        return lambdaQuery()
                .eq(SkillDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(SkillDefinitionEntity::getSortIndex, SkillDefinitionEntity::getDisplayName)
                .list();
    }

    public List<SkillDefinitionEntity> listActiveByKeys(Collection<String> skillKeys) {
        if (skillKeys == null || skillKeys.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(SkillDefinitionEntity::getSkillKey, skillKeys)
                .eq(SkillDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(SkillDefinitionEntity::getSortIndex, SkillDefinitionEntity::getDisplayName)
                .list();
    }
}
