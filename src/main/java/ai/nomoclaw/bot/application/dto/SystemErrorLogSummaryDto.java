package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;

public record SystemErrorLogSummaryDto(
        boolean hasErrors,
        long recent24hCount,
        LocalDateTime latestOccurredTime
) {
}
