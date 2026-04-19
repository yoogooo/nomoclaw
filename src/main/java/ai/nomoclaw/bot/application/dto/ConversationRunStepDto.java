package ai.nomoclaw.bot.application.dto;

import java.time.Instant;

public record ConversationRunStepDto(
        String stepUid,
        int roundIndex,
        int stepIndex,
        String status,
        String displayTitle,
        String displaySummary,
        String displayDetails,
        String policyReasonCode,
        Instant updatedTime
) {
}
