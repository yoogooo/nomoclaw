package ai.nomoclaw.bot.api.dto.conversation.request;

public record UpdateApprovalModeRequest(
        String approvalMode,
        Boolean applyToRunning
) {
}

