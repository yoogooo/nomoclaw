package ai.nomoclaw.bot.api.dto.agent.response;

import java.util.List;

public record AgentCatalogGroupResponse(
        String agentGroupUid,
        String groupName,
        String displayName,
        String avatar,
        String description,
        List<String> sceneTags,
        String collaborationMode,
        List<AgentCatalogAgentResponse> agents
) {
}
