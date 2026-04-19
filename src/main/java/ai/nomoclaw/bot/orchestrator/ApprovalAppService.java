package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ApprovalDecisionDto;
import ai.nomoclaw.bot.policy.tool.permission.PermissionScope;
import org.springframework.stereotype.Service;

@Service
public class ApprovalAppService {

    private final AgentApplicationService facade;

    public ApprovalAppService(AgentApplicationService facade) {
        this.facade = facade;
    }

    public ApprovalDecisionDto decideStep(String conversationUid, String stepUid, String action, String scope, String note) {
        return facade.decideStep(conversationUid, stepUid, action, PermissionScope.from(scope), note);
    }

    public void approveStep(String conversationUid, String stepUid) {
        facade.decideStep(conversationUid, stepUid, "allow", PermissionScope.ONCE, "");
    }

    public void rejectStep(String conversationUid, String stepUid) {
        facade.decideStep(conversationUid, stepUid, "deny", PermissionScope.ONCE, "");
    }
}
