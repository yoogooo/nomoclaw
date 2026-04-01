package ai.nomoclaw.bot.api;

public record CreateConversationRequest(
        String agentGroupUid,
        String agentUid
) {
}
