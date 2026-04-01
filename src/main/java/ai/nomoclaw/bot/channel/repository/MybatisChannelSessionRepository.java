package ai.nomoclaw.bot.channel.repository;

import ai.nomoclaw.bot.channel.model.ChannelSessionKey;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.spi.ChannelSessionRepository;
import ai.nomoclaw.bot.channel.store.entity.AgentChannelSessionEntity;
import ai.nomoclaw.bot.channel.store.repository.AgentChannelSessionRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class MybatisChannelSessionRepository implements ChannelSessionRepository {

    private final AgentChannelSessionRepository repository;

    public MybatisChannelSessionRepository(AgentChannelSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ChannelSessionRecord> find(ChannelSessionKey key) {
        AgentChannelSessionEntity entity = repository.findByChannelTenantAndSessionKey(
                key.channel().value(),
                key.tenantId(),
                key.sessionKey()
        );
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toRecord(entity));
    }

    @Override
    public ChannelSessionRecord upsert(ChannelSessionRecord record) {
        ChannelSessionKey key = record.key();
        LocalDateTime now = LocalDateTime.now();
        AgentChannelSessionEntity entity = repository.findByChannelTenantAndSessionKey(
                key.channel().value(),
                key.tenantId(),
                key.sessionKey()
        );
        if (entity == null) {
            entity = new AgentChannelSessionEntity();
            entity.setSessionUid(UUID.randomUUID().toString());
            entity.setChannel(key.channel().value());
            entity.setTenantId(key.tenantId());
            entity.setSessionKey(key.sessionKey());
            entity.setCreatedTime(now);
        }
        entity.setConversationUid(record.conversationUid());
        entity.setReplyTarget(record.replyTarget());
        entity.setRouteMetadata(JsonUtil.toJson(record.routeMetadata()));
        entity.setUpdatedTime(now);
        repository.saveOrUpdate(entity);
        return toRecord(entity);
    }

    private ChannelSessionRecord toRecord(AgentChannelSessionEntity entity) {
        return new ChannelSessionRecord(
                new ChannelSessionKey(
                        ChannelType.from(entity.getChannel()),
                        entity.getTenantId(),
                        entity.getSessionKey()
                ),
                entity.getConversationUid(),
                entity.getReplyTarget(),
                readMetadata(entity.getRouteMetadata())
        );
    }

    private Map<String, String> readMetadata(String routeMetadata) {
        if (routeMetadata == null || routeMetadata.isBlank()) {
            return Map.of();
        }
        Map<?, ?> map = JsonUtil.fromJsonQuietly(routeMetadata, Map.class).orElse(Map.of());
        if (map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        map.forEach((key, value) -> metadata.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        return Map.copyOf(metadata);
    }
}
