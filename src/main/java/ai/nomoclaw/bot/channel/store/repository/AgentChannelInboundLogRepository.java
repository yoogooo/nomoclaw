package ai.nomoclaw.bot.channel.store.repository;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import ai.nomoclaw.bot.channel.store.entity.AgentChannelInboundLogEntity;
import ai.nomoclaw.bot.channel.store.mapper.AgentChannelInboundLogMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AgentChannelInboundLogRepository extends CrudRepository<AgentChannelInboundLogMapper, AgentChannelInboundLogEntity> {

    public boolean existsByExternalMessageId(String channel, String tenantId, String externalMessageId) {
        long count = lambdaQuery()
                .eq(AgentChannelInboundLogEntity::getChannel, channel)
                .eq(AgentChannelInboundLogEntity::getTenantId, tenantId)
                .eq(AgentChannelInboundLogEntity::getExternalMessageId, externalMessageId)
                .count();
        return count > 0;
    }
}
