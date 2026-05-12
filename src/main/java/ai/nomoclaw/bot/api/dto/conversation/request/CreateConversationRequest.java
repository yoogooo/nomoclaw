package ai.nomoclaw.bot.api.dto.conversation.request;

public record CreateConversationRequest(
        String agentGroupUid,
        String agentUid
) {
}
