package ai.nomoclaw.bot.api;

public record ApprovalDecisionResponse(
        String status,
        String appliedScope,
        boolean persisted,
        String matchedRuleId
) {
}
