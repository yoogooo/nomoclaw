package ai.nomoclaw.bot.policy.tool.permission;

import ai.nomoclaw.bot.policy.tool.ToolPolicyReasonCode;

public record PermissionDecision(
        PermissionEffect effect,
        ToolPolicyReasonCode reasonCode,
        String message,
        PermissionSource matchedSource,
        String matchedRuleId,
        String pathSummary,
        boolean hardGuardHit
) {
}
