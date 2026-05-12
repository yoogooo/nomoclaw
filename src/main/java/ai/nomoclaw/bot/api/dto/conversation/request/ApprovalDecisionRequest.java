package ai.nomoclaw.bot.api.dto.conversation.request;

public record ApprovalDecisionRequest(
        String action,
        String scope,
        String note
) {
}
