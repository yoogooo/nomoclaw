package ai.nomoclaw.bot.store.query;

public record MessagePageQuery(
        String conversationUid,
        int limit,
        Long beforeId
) {
}
