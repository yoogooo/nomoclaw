package ai.nomoclaw.bot.orchestrator;

import org.springframework.stereotype.Service;

@Service
public class ApprovalAppService {

    private final AgentApplicationService facade;

    public ApprovalAppService(AgentApplicationService facade) {
        this.facade = facade;
    }

    public void approveStep(String conversationUid, String stepUid) {
        facade.approveStep(conversationUid, stepUid);
    }

    public void rejectStep(String conversationUid, String stepUid) {
        facade.rejectStep(conversationUid, stepUid);
    }
}
