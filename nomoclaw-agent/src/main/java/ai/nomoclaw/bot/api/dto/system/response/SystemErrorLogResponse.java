package ai.nomoclaw.bot.api.dto.system.response;

import java.time.LocalDateTime;

public record SystemErrorLogResponse(
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
