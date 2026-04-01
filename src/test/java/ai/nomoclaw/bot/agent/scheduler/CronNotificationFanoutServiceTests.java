package ai.nomoclaw.bot.agent.scheduler;

import ai.nomoclaw.bot.scheduler.CronChannelTargetResolver;
import ai.nomoclaw.bot.scheduler.CronNotificationFanoutService;
import ai.nomoclaw.bot.scheduler.CronSubscriptionRepository;
import ai.nomoclaw.bot.scheduler.config.CronNotifyProperties;
import ai.nomoclaw.bot.notification.NotificationSender;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CronNotificationFanoutServiceTests {

    @Test
    void shouldFanoutToMultipleSubscriptions() {
        NotificationSender sender = mock(NotificationSender.class);
        CronNotifyProperties props = notifyProps();
        CronChannelTargetResolver resolver = mock(CronChannelTargetResolver.class);
        CronSubscriptionRepository repository = new InMemorySubscriptionRepository(List.of(
                new CronSubscriptionRepository.CronSubscription("s1", "job-1", "feishu", "feishu:chat_id:1", true, LocalDateTime.now(), LocalDateTime.now()),
                new CronSubscriptionRepository.CronSubscription("s2", "job-1", "dingtalk", "dingtalk:session:https://x", true, LocalDateTime.now(), LocalDateTime.now())
        ));
        CronNotificationFanoutService service = new CronNotificationFanoutService(sender, repository, props, resolver);

        service.fanout(job("job-1"), "line1\nline2", null, Instant.now(), "COMPLETED");

        verify(sender, times(2)).send(any());
    }

    @Test
    void shouldRetryWhenSingleTargetFails() {
        NotificationSender sender = mock(NotificationSender.class);
        doThrow(new IllegalStateException("network")).doThrow(new IllegalStateException("network")).doThrow(new IllegalStateException("network"))
                .when(sender).send(any());
        CronNotifyProperties props = notifyProps();
        props.setRetryBackoffMs(1L);
        CronChannelTargetResolver resolver = mock(CronChannelTargetResolver.class);
        CronSubscriptionRepository repository = new InMemorySubscriptionRepository(List.of(
                new CronSubscriptionRepository.CronSubscription("s1", "job-1", "feishu", "feishu:chat_id:1", true, LocalDateTime.now(), LocalDateTime.now())
        ));
        CronNotificationFanoutService service = new CronNotificationFanoutService(sender, repository, props, resolver);

        service.fanout(job("job-1"), "line1\nline2", null, Instant.now(), "COMPLETED");

        verify(sender, times(3)).send(any());
    }

    @Test
    void shouldFallbackToLegacyRouteWhenNoSubscription() {
        NotificationSender sender = mock(NotificationSender.class);
        CronNotifyProperties props = notifyProps();
        CronChannelTargetResolver resolver = mock(CronChannelTargetResolver.class);
        CronSubscriptionRepository repository = new InMemorySubscriptionRepository(List.of());
        CronNotificationFanoutService service = new CronNotificationFanoutService(sender, repository, props, resolver);
        AgentCronJobEntity job = job("job-1");
        job.setExtConfig("{\"notification\":{\"channel\":\"dingtalk\",\"target\":\"dingtalk:session:https://legacy\"}}");

        service.fanout(job, "line1\nline2", null, Instant.now(), "COMPLETED");

        verify(sender, times(1)).send(any());
    }

    private CronNotifyProperties notifyProps() {
        CronNotifyProperties props = new CronNotifyProperties();
        props.setRetryMax(3);
        props.setRetryBackoffMs(1L);
        props.setSummaryMaxLines(5);
        return props;
    }

    private AgentCronJobEntity job(String jobUid) {
        AgentCronJobEntity entity = new AgentCronJobEntity();
        entity.setJobUid(jobUid);
        entity.setAgentUid("agent-1");
        entity.setTitle("天气播报");
        entity.setExpression("0 0 7 * * ?");
        entity.setTimezone("Asia/Shanghai");
        entity.setTaskContent("查询天气");
        return entity;
    }

    private static class InMemorySubscriptionRepository implements CronSubscriptionRepository {
        private final List<CronSubscription> data;
        private final AtomicInteger deletedCount = new AtomicInteger();

        private InMemorySubscriptionRepository(List<CronSubscription> data) {
            this.data = data;
        }

        @Override
        public List<CronSubscription> listByJobUid(String jobUid) {
            return data.stream().filter(item -> jobUid.equals(item.jobUid())).toList();
        }

        @Override
        public List<CronSubscription> listEnabledByJobUid(String jobUid) {
            return data.stream().filter(item -> jobUid.equals(item.jobUid()) && item.enabled()).toList();
        }

        @Override
        public void replace(String jobUid, List<CronSubscriptionUpsert> subscriptions) {
            // not needed in this test
        }

        @Override
        public void deleteByJobUid(String jobUid) {
            deletedCount.incrementAndGet();
        }
    }
}
