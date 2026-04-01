package ai.nomoclaw.bot.scheduler;

import java.time.LocalDateTime;
import java.util.List;

public interface CronSubscriptionRepository {

    List<CronSubscription> listByJobUid(String jobUid);

    List<CronSubscription> listEnabledByJobUid(String jobUid);

    void replace(String jobUid, List<CronSubscriptionUpsert> subscriptions);

    void deleteByJobUid(String jobUid);

    record CronSubscription(
            String subscriptionUid,
            String jobUid,
            String channel,
            String target,
            boolean enabled,
            LocalDateTime createdTime,
            LocalDateTime updatedTime
    ) {
    }

    record CronSubscriptionUpsert(
            String channel,
            String target,
            boolean enabled
    ) {
    }
}
