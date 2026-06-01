package ai.nomoclaw.bot.api.dto.agent.response;

import java.time.LocalDateTime;

public record AgentDocResponse(
        String key,
        String fileName,
        String content,
        LocalDateTime updatedTime
) {
}
