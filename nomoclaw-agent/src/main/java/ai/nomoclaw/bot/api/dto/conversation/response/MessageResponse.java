package ai.nomoclaw.bot.api.dto.conversation.response;

public record MessageResponse(
        String messageUid,
        String status,
        Integer roundsUsed,
        Integer maxRounds,
        String stopReason
) {
}
