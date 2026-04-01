package ai.nomoclaw.bot.policy.tool;

public record ToolPolicyDecisionResult(
        ToolPolicyDecision decision,
        ToolPolicyReasonCode reasonCode,
        String message,
        String pathSummary
) {

    public static ToolPolicyDecisionResult allow() {
        return new ToolPolicyDecisionResult(ToolPolicyDecision.ALLOW, ToolPolicyReasonCode.NONE, "", "");
    }

    public static ToolPolicyDecisionResult deny(ToolPolicyReasonCode reasonCode, String message, String pathSummary) {
        return new ToolPolicyDecisionResult(ToolPolicyDecision.DENY, reasonCode, message == null ? "" : message, pathSummary == null ? "" : pathSummary);
    }

    public static ToolPolicyDecisionResult ask(ToolPolicyReasonCode reasonCode, String message, String pathSummary) {
        return new ToolPolicyDecisionResult(ToolPolicyDecision.ASK, reasonCode, message == null ? "" : message, pathSummary == null ? "" : pathSummary);
    }

    public boolean denied() {
        return decision == ToolPolicyDecision.DENY;
    }

    public boolean asks() {
        return decision == ToolPolicyDecision.ASK;
    }
}
