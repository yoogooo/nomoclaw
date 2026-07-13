package ai.nomoclaw.bot.system.model;

import java.time.LocalDateTime;

public record SystemErrorLogSummaryDto(
        boolean hasErrors,
        long recent24hCount,
        LocalDateTime latestOccurredTime
) {
}
