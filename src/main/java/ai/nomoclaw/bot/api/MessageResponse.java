package ai.nomoclaw.bot.api;

public record MessageResponse(
        String messageUid,
        String status,
        Integer roundsUsed,
        Integer maxRounds,
        String stopReason
) {
}
