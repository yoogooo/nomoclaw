package ai.nomoclaw.bot.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.store.entity.AgentConversationEntity;
import ai.nomoclaw.bot.store.mapper.AgentConversationMapper;
import ai.nomoclaw.bot.store.query.ConversationSearchRow;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgentConversationRepository extends CrudRepository<AgentConversationMapper, AgentConversationEntity> {

    public AgentConversationEntity findByConversationUid(String conversationUid) {
        return lambdaQuery()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .last("LIMIT 1")
                .one();
    }

    public Long findSortIdByConversationUid(String conversationUid) {
        AgentConversationEntity entity = findByConversationUid(conversationUid);
        return entity == null ? null : entity.getId();
    }

    public List<AgentConversationEntity> listAllDesc() {
        return lambdaQuery()
                .orderByDesc(AgentConversationEntity::getPinned, AgentConversationEntity::getLastUserMessageTime, AgentConversationEntity::getId)
                .list();
    }

    public List<AgentConversationEntity> listAllDescExcludeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return listAllDesc();
        }
        return lambdaQuery()
                .ne(AgentConversationEntity::getChannel, channel)
                .orderByDesc(AgentConversationEntity::getPinned, AgentConversationEntity::getLastUserMessageTime, AgentConversationEntity::getId)
                .list();
    }

    public void deleteByConversationUid(String conversationUid) {
        lambdaUpdate()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .remove();
    }

    public List<AgentConversationEntity> listByAgentUid(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentConversationEntity::getAgentUid, agentUid)
                .orderByDesc(AgentConversationEntity::getPinned, AgentConversationEntity::getLastUserMessageTime, AgentConversationEntity::getId)
                .list();
    }

    public List<AgentConversationEntity> listConversationPage(String agentUid,
                                                              LocalDateTime asOf,
                                                              Integer beforePinned,
                                                              LocalDateTime beforeLastUserMessageTime,
                                                              Long beforeId,
                                                              int limit) {
        return getBaseMapper().listConversationPage(
                agentUid,
                asOf,
                beforePinned,
                beforeLastUserMessageTime,
                beforeId,
                limit
        );
    }

    public List<ConversationSearchRow> searchConversationPage(String agentUid,
                                                              String keywordPattern,
                                                              LocalDateTime beforeResultTime,
                                                              Long beforeConversationId,
                                                              int limit) {
        return getBaseMapper().searchConversationPage(
                agentUid,
                keywordPattern,
                beforeResultTime,
                beforeConversationId,
                limit
        );
    }
}
