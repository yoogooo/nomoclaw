package ai.nomoclaw.bot.conversation.model;

public record ApprovalDecisionDto(
        String status,
        String appliedScope,
        boolean persisted,
        String matchedRuleId
) {
}
