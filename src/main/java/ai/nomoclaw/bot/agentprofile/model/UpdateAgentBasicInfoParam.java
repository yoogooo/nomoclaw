package ai.nomoclaw.bot.agentprofile.model;

import java.util.List;

public record UpdateAgentBasicInfoParam(
        String displayName,
        String description,
        String avatar,
        String avatarColor,
        String modelProvider,
        String modelName,
        List<String> modelNames,
        String workspace
) {
}
