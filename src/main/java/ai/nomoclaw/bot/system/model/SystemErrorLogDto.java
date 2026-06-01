package ai.nomoclaw.bot.system.model;

import java.time.LocalDateTime;

public record SystemErrorLogDto(
        String logUid,
        String level,
        String source,
        String code,
        String title,
        String message,
        String detail,
        LocalDateTime occurredTime
) {
}
