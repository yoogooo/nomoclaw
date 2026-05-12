package ai.nomoclaw.bot.api.dto.agent.response;

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
        String workspace,
        String reportDir,
        String tmpDir,
        int sortIndex,
        List<String> capabilityTags,
        String memberRole,
        String responsibility,
        boolean primary
) {
}
