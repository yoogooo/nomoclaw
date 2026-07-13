package ai.nomoclaw.bot.channel.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.channel.store.entity.AgentChannelSessionEntity;
import ai.nomoclaw.bot.channel.store.mapper.AgentChannelSessionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentChannelSessionRepository extends CrudRepository<AgentChannelSessionMapper, AgentChannelSessionEntity> {

    public AgentChannelSessionEntity findByChannelTenantAndSessionKey(String channel, String tenantId, String sessionKey) {
        return lambdaQuery()
                .eq(AgentChannelSessionEntity::getChannel, channel)
                .eq(AgentChannelSessionEntity::getTenantId, tenantId)
                .eq(AgentChannelSessionEntity::getSessionKey, sessionKey)
                .last("LIMIT 1")
                .one();
    }

    public AgentChannelSessionEntity findLatestByChannel(String channel) {
        return lambdaQuery()
                .eq(AgentChannelSessionEntity::getChannel, channel)
                .orderByDesc(AgentChannelSessionEntity::getUpdatedTime, AgentChannelSessionEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public AgentChannelSessionEntity findLatestByChannelAndConversationUid(String channel, String conversationUid) {
        return lambdaQuery()
                .eq(AgentChannelSessionEntity::getChannel, channel)
                .eq(AgentChannelSessionEntity::getConversationUid, conversationUid)
                .orderByDesc(AgentChannelSessionEntity::getUpdatedTime, AgentChannelSessionEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public AgentChannelSessionEntity findLatestByConversationUid(String conversationUid) {
        return lambdaQuery()
                .eq(AgentChannelSessionEntity::getConversationUid, conversationUid)
                .orderByDesc(AgentChannelSessionEntity::getUpdatedTime, AgentChannelSessionEntity::getId)
                .last("LIMIT 1")
                .one();
    }

    public List<AgentChannelSessionEntity> listLatestByChannel(String channel, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return lambdaQuery()
                .eq(AgentChannelSessionEntity::getChannel, channel)
                .orderByDesc(AgentChannelSessionEntity::getUpdatedTime, AgentChannelSessionEntity::getId)
                .last("LIMIT " + safeLimit)
                .list();
    }
}
