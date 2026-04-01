package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentGroupMemberEntity;
import ai.nomoclaw.bot.store.mapper.AgentGroupMemberMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class AgentGroupMemberRepository extends CrudRepository<AgentGroupMemberMapper, AgentGroupMemberEntity> {

    public List<AgentGroupMemberEntity> listActiveByGroupUids(Collection<String> groupUids) {
        if (groupUids == null || groupUids.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(AgentGroupMemberEntity::getAgentGroupUid, groupUids)
                .eq(AgentGroupMemberEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentGroupMemberEntity::getSortIndex, AgentGroupMemberEntity::getId)
                .list();
    }

    public AgentGroupMemberEntity findPrimaryByGroupUid(String groupUid) {
        if (groupUid == null || groupUid.isBlank()) {
            return null;
        }
        AgentGroupMemberEntity primary = lambdaQuery()
                .eq(AgentGroupMemberEntity::getAgentGroupUid, groupUid)
                .eq(AgentGroupMemberEntity::getStatus, "ACTIVE")
                .eq(AgentGroupMemberEntity::getIsPrimary, 1)
                .orderByAsc(AgentGroupMemberEntity::getSortIndex, AgentGroupMemberEntity::getId)
                .last("LIMIT 1")
                .one();
        if (primary != null) {
            return primary;
        }
        return lambdaQuery()
                .eq(AgentGroupMemberEntity::getAgentGroupUid, groupUid)
                .eq(AgentGroupMemberEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentGroupMemberEntity::getSortIndex, AgentGroupMemberEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public AgentGroupMemberEntity findPrimaryByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return null;
        }
        AgentGroupMemberEntity primary = lambdaQuery()
                .eq(AgentGroupMemberEntity::getAgentUid, agentUid)
                .eq(AgentGroupMemberEntity::getStatus, "ACTIVE")
                .eq(AgentGroupMemberEntity::getIsPrimary, 1)
                .orderByAsc(AgentGroupMemberEntity::getSortIndex, AgentGroupMemberEntity::getId)
                .last("LIMIT 1")
                .one();
        if (primary != null) {
            return primary;
        }
        return lambdaQuery()
                .eq(AgentGroupMemberEntity::getAgentUid, agentUid)
                .eq(AgentGroupMemberEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentGroupMemberEntity::getSortIndex, AgentGroupMemberEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public long countByGroupUid(String groupUid) {
        if (groupUid == null || groupUid.isBlank()) {
            return 0L;
        }
        return lambdaQuery()
                .eq(AgentGroupMemberEntity::getAgentGroupUid, groupUid)
                .count();
    }
}
