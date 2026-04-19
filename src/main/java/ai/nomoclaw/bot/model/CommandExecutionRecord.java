package ai.nomoclaw.bot.model;

import java.time.Instant;

public record CommandExecutionRecord(
        String executionUid,
        String conversationUid,
        String messageUid,
        String stepUid,
        int attempt,
        String command,
        String cwd,
        String shell,
        Integer exitCode,
        boolean success,
        String stdout,
        String stderr,
        String output,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
