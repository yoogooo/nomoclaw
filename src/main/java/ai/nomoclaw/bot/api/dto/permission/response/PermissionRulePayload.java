package ai.nomoclaw.bot.api.dto.permission.response;

public record PermissionRulePayload(
        String ruleId,
        String effect,
        String tool,
        String action,
        String resourceType,
        String pathPattern,
        String commandPattern,
        String expiresAt,
        Boolean enabled
) {
}
