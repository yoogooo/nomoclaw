package ai.nomoclaw.bot.api;

import java.time.Instant;

public record ConversationRunStepResponse(
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
