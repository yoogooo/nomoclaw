package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ApprovalDecisionDto;
import ai.nomoclaw.bot.policy.tool.permission.PermissionScope;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalAppServiceTests {

    @Test
    void shouldPassSessionScopeToFacade() {
        AgentApplicationService facade = mock(AgentApplicationService.class);
        when(facade.decideStep(eq("c1"), eq("s1"), eq("allow"), eq(PermissionScope.SESSION), eq("")))
                .thenReturn(new ApprovalDecisionDto("accepted", "session", true, "r1"));
        ApprovalAppService service = new ApprovalAppService(facade);

        service.decideStep("c1", "s1", "allow", "session", "");

        verify(facade).decideStep(eq("c1"), eq("s1"), eq("allow"), eq(PermissionScope.SESSION), eq(""));
    }

    @Test
    void shouldPassUserScopeToFacade() {
        AgentApplicationService facade = mock(AgentApplicationService.class);
        when(facade.decideStep(eq("c2"), eq("s2"), eq("allow"), eq(PermissionScope.USER), eq("note")))
                .thenReturn(new ApprovalDecisionDto("accepted", "user", true, "r2"));
        ApprovalAppService service = new ApprovalAppService(facade);

        service.decideStep("c2", "s2", "allow", "user", "note");

        verify(facade).decideStep(eq("c2"), eq("s2"), eq("allow"), eq(PermissionScope.USER), eq("note"));
    }
}
