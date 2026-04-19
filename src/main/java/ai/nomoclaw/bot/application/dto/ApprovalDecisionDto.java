package ai.nomoclaw.bot.application.dto;

public record ApprovalDecisionDto(
        String status,
        String appliedScope,
        boolean persisted,
        String matchedRuleId
) {
}
