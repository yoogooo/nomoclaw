package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.store.entity.AgentCronSubscriptionEntity;
import ai.nomoclaw.bot.store.repository.AgentCronSubscriptionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MybatisCronSubscriptionRepository implements CronSubscriptionRepository {

    private final AgentCronSubscriptionRepository repository;

    public MybatisCronSubscriptionRepository(AgentCronSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CronSubscription> listByJobUid(String jobUid) {
        return repository.listByJobUid(trim(jobUid)).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<CronSubscription> listEnabledByJobUid(String jobUid) {
        return repository.listEnabledByJobUid(trim(jobUid)).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public void replace(String jobUid, List<CronSubscriptionUpsert> subscriptions) {
        String normalizedJobUid = trim(jobUid);
        repository.deleteByJobUid(normalizedJobUid);
        List<CronSubscriptionUpsert> normalized = normalize(subscriptions);
        if (normalized.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<AgentCronSubscriptionEntity> entities = new ArrayList<>(normalized.size());
        for (CronSubscriptionUpsert item : normalized) {
            AgentCronSubscriptionEntity entity = new AgentCronSubscriptionEntity();
            entity.setSubscriptionUid(UUID.randomUUID().toString());
            entity.setJobUid(normalizedJobUid);
            entity.setChannel(ChannelType.from(item.channel()).value());
            entity.setTarget(item.target().trim());
            entity.setBotId(trim(item.botId()));
            entity.setEnabled(item.enabled() ? 1 : 0);
            entity.setCreatedTime(now);
            entity.setUpdatedTime(now);
            entities.add(entity);
        }
        repository.saveBatch(entities, 100);
    }

    @Override
    public void deleteByJobUid(String jobUid) {
        repository.deleteByJobUid(trim(jobUid));
    }

    private List<CronSubscriptionUpsert> normalize(List<CronSubscriptionUpsert> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return List.of();
        }
        Map<String, CronSubscriptionUpsert> unique = new LinkedHashMap<>();
        for (CronSubscriptionUpsert item : subscriptions) {
            if (item == null) {
                continue;
            }
            String channel = ChannelType.from(item.channel()).value();
            if ("noop".equals(channel) || "web".equals(channel)) {
                continue;
            }
            String target = trim(item.target());
            String botId = trim(item.botId());
            String key = channel + "|" + target + "|" + botId;
            unique.put(key, new CronSubscriptionUpsert(channel, target, botId, item.enabled()));
        }
        return List.copyOf(unique.values());
    }

    private CronSubscription toRecord(AgentCronSubscriptionEntity entity) {
        return new CronSubscription(
                entity.getSubscriptionUid(),
                entity.getJobUid(),
                ChannelType.from(entity.getChannel()).value(),
                entity.getTarget(),
                trim(entity.getBotId()),
                entity.getEnabled() != null && entity.getEnabled() == 1,
                entity.getCreatedTime(),
                entity.getUpdatedTime()
        );
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
