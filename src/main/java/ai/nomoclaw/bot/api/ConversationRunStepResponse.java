package ai.nomoclaw.bot.api;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record ConversationRunStepResponse(
        String stepUid,
        int roundIndex,
        int stepIndex,
        String status,
        String toolName,
        JsonNode toolArgs,
        String displayTitle,
        String displaySummary,
        String displayDetails,
        String policyReasonCode,
        Instant updatedTime
) {
}
