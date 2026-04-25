package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;
import java.util.List;

public record SkillBindingsResponse(
        String skillKey,
        String displayName,
        String description,
        String skillPath,
        String status,
        LocalDateTime updatedTime,
        int enabledAgentCount,
        List<SkillBindingAgentResponse> agentBindings
) {
}
