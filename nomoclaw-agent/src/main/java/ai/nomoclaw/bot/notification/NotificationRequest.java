package ai.nomoclaw.bot.notification;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public record NotificationRequest(
        String jobUid,
        String agentUid,
        String title,
        String summary,
        Path reportPath,
        String channel,
        String target,
        Instant executedAt,
        Map<String, String> metadata
) {
}
