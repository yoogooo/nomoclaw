package ai.nomoclaw.bot.api;

import java.time.Instant;
import java.util.List;

public record ConversationMessageRunResponse(
        String messageUid,
        String status,
        String summary,
        int completedSteps,
        int totalSteps,
        Instant updatedTime,
        List<ConversationRunStepResponse> steps
) {
}
