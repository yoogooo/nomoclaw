package ai.nomoclaw.bot.api;

public record ApprovalDecisionRequest(
        String action,
        String scope,
        String note
) {
}
