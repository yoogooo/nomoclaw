package ai.nomoclaw.bot.api.dto.system.response;

import java.time.LocalDateTime;

public record SystemErrorLogSummaryResponse(
        boolean hasErrors,
        long recent24hCount,
        LocalDateTime latestOccurredTime
) {
}
