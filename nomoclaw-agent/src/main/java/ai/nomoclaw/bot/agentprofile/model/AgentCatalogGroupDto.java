package ai.nomoclaw.bot.agentprofile.model;

import java.util.List;

public record AgentCatalogGroupDto(
        String agentGroupUid,
        String groupName,
        String displayName,
        String avatar,
        String description,
        List<String> sceneTags,
        String collaborationMode,
        List<AgentCatalogAgentDto> agents
) {
}
