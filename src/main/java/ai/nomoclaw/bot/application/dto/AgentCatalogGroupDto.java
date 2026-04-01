package ai.nomoclaw.bot.application.dto;

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
