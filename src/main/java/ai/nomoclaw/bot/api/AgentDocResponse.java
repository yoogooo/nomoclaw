package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record AgentDocResponse(
        String key,
        String fileName,
        String content,
        LocalDateTime updatedTime
) {
}
