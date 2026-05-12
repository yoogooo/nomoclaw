package ai.nomoclaw.bot.api.dto.conversation.response;

public record ApprovalDecisionResponse(
        String status,
        String appliedScope,
        boolean persisted,
        String matchedRuleId
) {
}
