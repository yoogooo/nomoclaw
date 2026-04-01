package ai.nomoclaw.bot.notification;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.spi.ChannelMessageRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Primary
@Slf4j
public class RoutingNotificationSender implements NotificationSender {

    private final ChannelMessageRouter channelMessageRouter;
    private final NoopNotificationSender noopNotificationSender;

    public RoutingNotificationSender(ChannelMessageRouter channelMessageRouter,
                                     NoopNotificationSender noopNotificationSender) {
        this.channelMessageRouter = channelMessageRouter;
        this.noopNotificationSender = noopNotificationSender;
    }

    @Override
    public void send(NotificationRequest request) {
        ChannelType channelType = ChannelType.from(request.channel());
        if (channelType == ChannelType.NOOP || channelType == ChannelType.WEB) {
            noopNotificationSender.send(request);
            return;
        }
        String target = request.target() == null ? "" : request.target().trim();
        if (target.isBlank()) {
            noopNotificationSender.send(request);
            return;
        }
        String text = buildText(request);
        Map<String, String> metadata = new LinkedHashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        metadata.put("jobUid", request.jobUid());
        metadata.put("agentUid", request.agentUid());
        channelMessageRouter.send(channelType, target, text, Map.copyOf(metadata));
        log.info("[Notification] routed channel={} target={} title={}", channelType.value(), target, request.title());
    }

    private String buildText(NotificationRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.title() == null ? "任务通知" : request.title());
        if (request.summary() != null && !request.summary().isBlank()) {
            builder.append("\n\n").append(request.summary());
        }
        Path reportPath = request.reportPath();
        if (reportPath != null) {
            builder.append("\n\n报告: ").append(reportPath.toAbsolutePath().normalize());
        }
        return builder.toString();
    }
}
