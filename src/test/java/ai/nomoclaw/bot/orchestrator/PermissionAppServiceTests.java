package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.policy.tool.permission.HardGuardService;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEffect;
import ai.nomoclaw.bot.policy.tool.permission.PermissionRule;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSettingsStore;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSource;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PermissionAppServiceTests {

    @Test
    void screenshotApprovalRuleShouldNotBindSpecificPath() {
        PermissionAppService service = new PermissionAppService(
                mock(ToolPermissionPolicyService.class),
                mock(PermissionSettingsStore.class),
                mock(AgentDefinitionRepository.class),
                mock(HardGuardService.class)
        );
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("path", "tmp/dingtalk_now.png");

        PermissionRule rule = service.ruleFromApproval(
                "DesktopScreenshotTool",
                args,
                PermissionEffect.ALLOW,
                PermissionSource.SESSION,
                Path.of(".").toAbsolutePath().normalize()
        );

        assertEquals("DesktopScreenshotTool", rule.tool());
        assertEquals("*", rule.action());
        assertTrue(rule.pathPattern().isBlank());
    }

    @Test
    void browserScreenshotApprovalRuleShouldNotBindSpecificPath() {
        PermissionAppService service = new PermissionAppService(
                mock(ToolPermissionPolicyService.class),
                mock(PermissionSettingsStore.class),
                mock(AgentDefinitionRepository.class),
                mock(HardGuardService.class)
        );
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("action", "screenshot");
        args.put("output", "tmp/page.png");
        args.put("path", "tmp/page.png");

        PermissionRule rule = service.ruleFromApproval(
                "BrowserTool",
                args,
                PermissionEffect.ALLOW,
                PermissionSource.SESSION,
                Path.of(".").toAbsolutePath().normalize()
        );

        assertEquals("BrowserTool", rule.tool());
        assertEquals("screenshot", rule.action());
        assertTrue(rule.pathPattern().isBlank());
    }
}
