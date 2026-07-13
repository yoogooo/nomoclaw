package ai.nomoclaw.bot.policy.tool;

import ai.nomoclaw.bot.policy.tool.permission.PermissionSource;

public record ToolPolicyDecisionResult(
        ToolPolicyDecision decision,
        ToolPolicyReasonCode reasonCode,
        String message,
        String pathSummary,
        PermissionSource matchedSource,
        String matchedRuleId,
        boolean hardGuardHit
) {

    public static ToolPolicyDecisionResult allow() {
        return new ToolPolicyDecisionResult(ToolPolicyDecision.ALLOW, ToolPolicyReasonCode.NONE, "", "", null, "", false);
    }

    public static ToolPolicyDecisionResult deny(ToolPolicyReasonCode reasonCode, String message, String pathSummary) {
        return new ToolPolicyDecisionResult(ToolPolicyDecision.DENY, reasonCode, message == null ? "" : message, pathSummary == null ? "" : pathSummary, null, "", false);
    }

    public static ToolPolicyDecisionResult ask(ToolPolicyReasonCode reasonCode, String message, String pathSummary) {
        return new ToolPolicyDecisionResult(ToolPolicyDecision.ASK, reasonCode, message == null ? "" : message, pathSummary == null ? "" : pathSummary, null, "", false);
    }

    public static ToolPolicyDecisionResult of(ToolPolicyDecision decision,
                                              ToolPolicyReasonCode reasonCode,
                                              String message,
                                              String pathSummary,
                                              PermissionSource matchedSource,
                                              String matchedRuleId,
                                              boolean hardGuardHit) {
        return new ToolPolicyDecisionResult(
                decision,
                reasonCode == null ? ToolPolicyReasonCode.NONE : reasonCode,
                message == null ? "" : message,
                pathSummary == null ? "" : pathSummary,
                matchedSource,
                matchedRuleId == null ? "" : matchedRuleId,
                hardGuardHit
        );
    }

    public boolean denied() {
        return decision == ToolPolicyDecision.DENY;
    }

    public boolean asks() {
        return decision == ToolPolicyDecision.ASK;
    }
}
