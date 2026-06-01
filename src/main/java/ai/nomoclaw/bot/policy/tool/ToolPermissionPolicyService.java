package ai.nomoclaw.bot.policy.tool;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.policy.tool.permission.PermissionDecision;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEffect;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEngine;
import ai.nomoclaw.bot.policy.tool.permission.PermissionRule;
import ai.nomoclaw.bot.policy.tool.permission.PermissionScope;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolPermissionPolicyService {

    private final PermissionEngine permissionEngine;

    public ToolPermissionPolicyService(PermissionEngine permissionEngine) {
        this.permissionEngine = permissionEngine;
    }

    public ToolPolicyDecisionResult evaluate(ToolPolicyContext context) {
        PermissionDecision decision = permissionEngine.evaluate(context);
        if (decision == null) {
            return ToolPolicyDecisionResult.allow();
        }
        ToolPolicyDecision mapped = switch (decision.effect()) {
            case ALLOW -> ToolPolicyDecision.ALLOW;
            case ASK -> ToolPolicyDecision.ASK;
            case DENY -> ToolPolicyDecision.DENY;
        };
        return ToolPolicyDecisionResult.of(
                mapped,
                decision.reasonCode(),
                decision.message(),
                decision.pathSummary(),
                decision.matchedSource(),
                decision.matchedRuleId(),
                decision.hardGuardHit()
        );
    }

    public void persistRule(PermissionScope scope, String conversationUid, String agentName, PermissionRule rule) {
        permissionEngine.persistRule(scope, conversationUid, agentName, rule);
    }

    public List<PermissionRule> effectiveRules(String agentName, String conversationUid) {
        return permissionEngine.effectiveRules(agentName, conversationUid);
    }

    public PermissionRule newRule(PermissionSource source,
                                  PermissionEffect effect,
                                  String tool,
                                  String action,
                                  String pathPattern,
                                  String commandPattern) {
        return new PermissionRule(
                UuidUtil.newUuid(),
                source,
                effect,
                tool == null || tool.isBlank() ? "*" : tool,
                action == null || action.isBlank() ? "*" : action,
                ai.nomoclaw.bot.policy.tool.permission.PermissionResourceType.fromTool(tool),
                pathPattern == null ? "" : pathPattern,
                commandPattern == null ? "" : commandPattern,
                null,
                true
        );
    }
}
