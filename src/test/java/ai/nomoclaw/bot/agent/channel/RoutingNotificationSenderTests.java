package ai.nomoclaw.bot.agent.channel;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.spi.ChannelMessageRouter;
import ai.nomoclaw.bot.notification.NoopNotificationSender;
import ai.nomoclaw.bot.notification.NotificationRequest;
import ai.nomoclaw.bot.notification.RoutingNotificationSender;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RoutingNotificationSenderTests {

    @Test
    void shouldFallbackToNoopForNoopChannel() {
        ChannelMessageRouter router = mock(ChannelMessageRouter.class);
        NoopNotificationSender noop = mock(NoopNotificationSender.class);
        RoutingNotificationSender sender = new RoutingNotificationSender(router, noop);

        NotificationRequest request = new NotificationRequest(
                "job-1", "agent-1", "title", "summary", null,
                "noop", "", Instant.now(), Map.of()
        );

        sender.send(request);

        verify(noop, times(1)).send(request);
        verify(router, never()).send(any(), any(), any(), any());
    }

    @Test
    void shouldRoutePlatformChannel() {
        ChannelMessageRouter router = mock(ChannelMessageRouter.class);
        NoopNotificationSender noop = mock(NoopNotificationSender.class);
        RoutingNotificationSender sender = new RoutingNotificationSender(router, noop);

        NotificationRequest request = new NotificationRequest(
                "job-2", "agent-2", "title", "summary", Path.of("/tmp/report.md"),
                "feishu", "https://example.com/webhook", Instant.now(), Map.of("k", "v")
        );

        sender.send(request);

        verify(router, times(1)).send(eq(ChannelType.FEISHU), eq("https://example.com/webhook"), any(), any());
        verify(noop, never()).send(any());
    }
}
