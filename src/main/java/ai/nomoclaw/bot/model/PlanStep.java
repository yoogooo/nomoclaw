package ai.nomoclaw.bot.model;

import tools.jackson.databind.JsonNode;

public record PlanStep(
        String stepUid,
        int roundIndex,
        int stepIndex,
        String title,
        String toolName,
        JsonNode toolArgs,
        RiskLevel riskLevel,
        String doneCriteria,
        StepStatus status,
        int retryCount,
        String lastError,
        String outputText,
        ApprovalStatus approvalStatus
) {
}
