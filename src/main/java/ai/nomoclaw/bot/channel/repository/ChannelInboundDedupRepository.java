package ai.nomoclaw.bot.channel.repository;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.store.entity.AgentChannelInboundLogEntity;
import ai.nomoclaw.bot.channel.store.repository.AgentChannelInboundLogRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ChannelInboundDedupRepository {

    private final AgentChannelInboundLogRepository repository;

    public ChannelInboundDedupRepository(AgentChannelInboundLogRepository repository) {
        this.repository = repository;
    }

    public boolean exists(InboundEnvelope envelope) {
        if (envelope.externalMessageId().isBlank()) {
            return false;
        }
        return repository.existsByExternalMessageId(
                envelope.channel().value(),
                envelope.tenantId(),
                envelope.externalMessageId()
        );
    }

    public void save(InboundEnvelope envelope) {
        if (envelope.externalMessageId().isBlank()) {
            return;
        }
        AgentChannelInboundLogEntity entity = new AgentChannelInboundLogEntity();
        entity.setLogUid(UuidUtil.newUuid());
        entity.setChannel(envelope.channel().value());
        entity.setTenantId(envelope.tenantId());
        entity.setSessionKey(envelope.sessionKey());
        entity.setExternalMessageId(envelope.externalMessageId());
        entity.setCreatedTime(LocalDateTime.now());
        repository.save(entity);
    }
}
