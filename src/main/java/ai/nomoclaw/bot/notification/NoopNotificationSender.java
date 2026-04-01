package ai.nomoclaw.bot.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NoopNotificationSender implements NotificationSender {

    @Override
    public void send(NotificationRequest request) {
        log.info("[Notification] noop channel={} target={} title={} reportPath={}",
                request.channel(), request.target(), request.title(), request.reportPath());
    }
}
