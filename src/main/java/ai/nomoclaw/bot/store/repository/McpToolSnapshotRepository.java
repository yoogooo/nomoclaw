package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.McpToolSnapshotEntity;
import ai.nomoclaw.bot.store.mapper.McpToolSnapshotMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class McpToolSnapshotRepository extends CrudRepository<McpToolSnapshotMapper, McpToolSnapshotEntity> {

    public McpToolSnapshotEntity findActiveByToolKey(String toolKey) {
        if (toolKey == null || toolKey.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(McpToolSnapshotEntity::getToolKey, toolKey)
                .eq(McpToolSnapshotEntity::getStatus, "ACTIVE")
                .one();
    }

    public McpToolSnapshotEntity findByServerUidAndOriginalToolName(String serverUid, String originalToolName) {
        if (serverUid == null || serverUid.isBlank() || originalToolName == null || originalToolName.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(McpToolSnapshotEntity::getServerUid, serverUid)
                .eq(McpToolSnapshotEntity::getOriginalToolName, originalToolName)
                .one();
    }

    public List<McpToolSnapshotEntity> listActive() {
        return lambdaQuery()
                .eq(McpToolSnapshotEntity::getStatus, "ACTIVE")
                .orderByAsc(McpToolSnapshotEntity::getToolKey)
                .list();
    }

    public List<McpToolSnapshotEntity> listByServerUid(String serverUid) {
        if (serverUid == null || serverUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(McpToolSnapshotEntity::getServerUid, serverUid)
                .orderByAsc(McpToolSnapshotEntity::getToolKey)
                .list();
    }

    public List<McpToolSnapshotEntity> listActiveByToolKeys(Collection<String> toolKeys) {
        if (toolKeys == null || toolKeys.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(McpToolSnapshotEntity::getToolKey, toolKeys)
                .eq(McpToolSnapshotEntity::getStatus, "ACTIVE")
                .list();
    }
}
