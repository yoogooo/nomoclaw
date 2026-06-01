package ai.nomoclaw.bot.orchestrator.approval;

import ai.nomoclaw.bot.conversation.model.ApprovalDecisionDto;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import ai.nomoclaw.bot.policy.tool.permission.PermissionScope;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ApprovalAppService {

    private final AgentApplicationService facade;

    public ApprovalAppService(AgentApplicationService facade) {
        this.facade = facade;
    }

    public ApprovalDecisionDto decideStep(String conversationUid, String stepUid, String action, String scope, String note) {
        String normalizedAction = normalizeAction(action);
        PermissionScope appliedScope = normalizeScope(scope);
        return facade.decideStep(conversationUid, stepUid, normalizedAction, appliedScope, note);
    }

    public void approveStep(String conversationUid, String stepUid) {
        facade.decideStep(conversationUid, stepUid, "allow", PermissionScope.ONCE, "");
    }

    public void rejectStep(String conversationUid, String stepUid) {
        facade.decideStep(conversationUid, stepUid, "deny", PermissionScope.ONCE, "");
    }

    private String normalizeAction(String action) {
        String value = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return "deny".equals(value) ? "deny" : "allow";
    }

    private PermissionScope normalizeScope(String scope) {
        PermissionScope parsed = PermissionScope.from(scope);
        return switch (parsed) {
            case SESSION -> PermissionScope.SESSION;
            case AGENT -> PermissionScope.AGENT;
            case USER -> PermissionScope.USER;
            case ONCE -> PermissionScope.ONCE;
        };
    }
}
