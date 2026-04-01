package ai.nomoclaw.bot.api;

import java.util.List;

public record AgentCatalogAgentResponse(
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
