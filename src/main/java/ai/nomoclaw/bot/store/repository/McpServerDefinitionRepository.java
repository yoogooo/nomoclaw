package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.McpServerDefinitionEntity;
import ai.nomoclaw.bot.store.mapper.McpServerDefinitionMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class McpServerDefinitionRepository extends CrudRepository<McpServerDefinitionMapper, McpServerDefinitionEntity> {

    public McpServerDefinitionEntity findByUid(String serverUid) {
        if (serverUid == null || serverUid.isBlank()) {
            return null;
        }
        return lambdaQuery().eq(McpServerDefinitionEntity::getServerUid, serverUid).one();
    }

    public McpServerDefinitionEntity findByServerName(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return null;
        }
        return lambdaQuery().eq(McpServerDefinitionEntity::getServerName, serverName).one();
    }

    public List<McpServerDefinitionEntity> listActive() {
        return lambdaQuery()
                .eq(McpServerDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(McpServerDefinitionEntity::getId)
                .list();
    }

    public List<McpServerDefinitionEntity> listAll() {
        return lambdaQuery()
                .orderByAsc(McpServerDefinitionEntity::getId)
                .list();
    }
}
