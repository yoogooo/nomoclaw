package ai.nomoclaw.bot.policy.tool.permission;

import java.time.Instant;

public record PermissionRule(
        String ruleId,
        PermissionSource source,
        PermissionEffect effect,
        String tool,
        String action,
        PermissionResourceType resourceType,
        String pathPattern,
        String commandPattern,
        Instant expiresAt,
        boolean enabled
) {
    public boolean isExpired(Instant now) {
        return expiresAt != null && now != null && expiresAt.isBefore(now);
    }
}
