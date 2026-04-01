package ai.nomoclaw.bot.application.dto;

import java.util.List;

public record AgentCatalogAgentDto(
        String agentUid,
        String agentName,
        String displayName,
        String avatar,
        String avatarColor,
        String description,
        String modelProvider,
        String modelName,
        List<String> modelNames,
        int sortIndex,
        List<String> capabilityTags,
        String memberRole,
        String responsibility,
        boolean primary
) {
}
