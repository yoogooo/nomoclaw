package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.policy.tool.ToolPolicyContext;
import ai.nomoclaw.bot.policy.tool.ToolPolicyReasonCode;
import ai.nomoclaw.bot.policy.tool.permission.CommandRuleResolver;
import ai.nomoclaw.bot.policy.tool.permission.HardGuardService;
import ai.nomoclaw.bot.policy.tool.permission.PermissionDecision;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEffect;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEngine;
import ai.nomoclaw.bot.policy.tool.permission.PermissionResourceType;
import ai.nomoclaw.bot.policy.tool.permission.PermissionRule;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSettingsStore;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSource;
import ai.nomoclaw.bot.policy.tool.permission.SessionPermissionStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionEngineTests {

    @Test
    void denyShouldWinAcrossSourcesEvenIfSessionAllowExists() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        sessionStore.addRule("c1", rule("r-session-allow", PermissionSource.SESSION, PermissionEffect.ALLOW));
        settings.userRules = List.of(rule("r-user-deny", PermissionSource.USER_SETTINGS, PermissionEffect.DENY));

        PermissionDecision decision = engine.evaluate(commandContext("c1", "ls -la"));
        assertEquals(PermissionEffect.DENY, decision.effect());
        assertEquals(PermissionSource.USER_SETTINGS, decision.matchedSource());
        assertEquals("r-user-deny", decision.matchedRuleId());
    }

    @Test
    void sourcePriorityShouldApplyWithinSameBehavior() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        sessionStore.addRule("c2", rule("r-session-deny", PermissionSource.SESSION, PermissionEffect.DENY));
        settings.userRules = List.of(rule("r-user-deny", PermissionSource.USER_SETTINGS, PermissionEffect.DENY));

        PermissionDecision decision = engine.evaluate(commandContext("c2", "ls -la"));
        assertEquals(PermissionEffect.DENY, decision.effect());
        assertEquals(PermissionSource.SESSION, decision.matchedSource());
        assertEquals("r-session-deny", decision.matchedRuleId());
    }

    @Test
    void builtinReadonlyToolShouldAllowByDefault() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        PermissionDecision decision = engine.evaluate(new ToolPolicyContext(
                "memory_search_tool",
                args,
                Path.of(".").toAbsolutePath().normalize(),
                "agent-uid",
                "default",
                "local",
                "c3",
                "m1",
                "s1"
        ));
        assertEquals(PermissionEffect.ALLOW, decision.effect());
        assertEquals(ToolPolicyReasonCode.RULE_ALLOW_MATCHED, decision.reasonCode());
    }

    @Test
    void imageLoaderToolShouldAllowByDefault() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("reference", "刚才截图");
        PermissionDecision decision = engine.evaluate(new ToolPolicyContext(
                "image_loader_tool",
                args,
                Path.of(".").toAbsolutePath().normalize(),
                "agent-uid",
                "default",
                "local",
                "c3-image",
                "m1",
                "s1"
        ));
        assertEquals(PermissionEffect.ALLOW, decision.effect());
        assertEquals(ToolPolicyReasonCode.RULE_ALLOW_MATCHED, decision.reasonCode());
    }

    @Test
    void hardGuardShouldBlockSystemPathWriteBeforeRules() {
        StubSettingsStore settings = new StubSettingsStore();
        settings.userRules = List.of(rule("r-user-allow", PermissionSource.USER_SETTINGS, PermissionEffect.ALLOW));
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        PermissionDecision decision = engine.evaluate(commandContext("c4", "mkdir -p /etc/test"));
        assertEquals(PermissionEffect.DENY, decision.effect());
        assertEquals(ToolPolicyReasonCode.HARD_GUARD_SYSTEM_PATH_DENY, decision.reasonCode());
        assertTrue(decision.hardGuardHit());
    }

    @Test
    void linuxReadonlyCommandShouldAllowByBaseline() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        PermissionDecision decision = engine.evaluate(commandContext("c5", "ls -lhS ~/Downloads | head -30"));
        assertEquals(PermissionEffect.ALLOW, decision.effect());
        assertEquals("builtin-command-readonly-allow", decision.matchedRuleId());
    }

    @Test
    void windowsPowershellReadonlyCommandShouldAllowByBaseline() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        PermissionDecision decision = engine.evaluate(commandContext(
                "c6",
                "powershell -Command \"Get-ChildItem C:\\\\Users\\\\sun\\\\Downloads | Select-Object -First 30\""
        ));
        assertEquals(PermissionEffect.ALLOW, decision.effect());
        assertEquals("builtin-command-readonly-allow", decision.matchedRuleId());
    }

    @Test
    void commandWithWriteRedirectionShouldAsk() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        PermissionDecision decision = engine.evaluate(commandContext("c7", "ls -la > out.txt"));
        assertEquals(PermissionEffect.ASK, decision.effect());
        assertEquals(ToolPolicyReasonCode.DEFAULT_REQUIRE_APPROVAL, decision.reasonCode());
    }

    @Test
    void commandWithDanglingSingleQuoteShouldNotCrash() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        PermissionDecision decision = assertDoesNotThrow(() -> engine.evaluate(commandContext("c7-quote", "echo '")));
        assertNotNull(decision);
        assertNotNull(decision.effect());
    }

    @Test
    void desktopScreenshotAllowRuleWithPathShouldMatch() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        sessionStore.addRule("c-shot", new PermissionRule(
                "r-shot-allow",
                PermissionSource.SESSION,
                PermissionEffect.ALLOW,
                "desktop_screenshot_tool",
                "*",
                PermissionResourceType.ANY,
                "tmp/dingtalk_now.png",
                "",
                null,
                true
        ));

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("path", "tmp/dingtalk_now.png");
        PermissionDecision decision = engine.evaluate(new ToolPolicyContext(
                "desktop_screenshot_tool",
                args,
                Path.of(".").toAbsolutePath().normalize(),
                "agent-uid",
                "default",
                "local",
                "c-shot",
                "m1",
                "s1"
        ));

        assertEquals(PermissionEffect.ALLOW, decision.effect());
        assertEquals("r-shot-allow", decision.matchedRuleId());
    }

    @Test
    void denyRuleShouldOverrideReadonlyBaselineAllow() {
        StubSettingsStore settings = new StubSettingsStore();
        settings.userRules = List.of(rule("r-user-deny-readonly", PermissionSource.USER_SETTINGS, PermissionEffect.DENY));
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        PermissionDecision decision = engine.evaluate(commandContext("c8", "ls -la"));
        assertEquals(PermissionEffect.DENY, decision.effect());
        assertEquals("r-user-deny-readonly", decision.matchedRuleId());
    }

    @Test
    void sensitiveSshPathReadShouldAsk() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        PermissionDecision decision = engine.evaluate(commandContext("c9", "cat ~/.ssh/config"));
        assertEquals(PermissionEffect.ASK, decision.effect());
        assertEquals(ToolPolicyReasonCode.HARD_GUARD_SENSITIVE_PATH_READ_ASK, decision.reasonCode());
        assertTrue(decision.hardGuardHit());
    }

    @Test
    void browserOpenShouldAllowByBaseline() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("action", "open");
        args.put("url", "https://weather.com.cn/weather/101010100.shtml");

        PermissionDecision decision = engine.evaluate(new ToolPolicyContext(
                "browser_tool",
                args,
                Path.of(".").toAbsolutePath().normalize(),
                "agent-uid",
                "default",
                "local",
                "c10",
                "m1",
                "s1"
        ));

        assertEquals(PermissionEffect.ALLOW, decision.effect());
        assertEquals("builtin-browser-open-allow", decision.matchedRuleId());
    }

    @Test
    void splitCronToolsShouldAllowByBaseline() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("task", "提醒用户观看新闻联播");
        args.put("expression", "0 30 19 * * ?");

        for (String toolName : List.of("CronCreateTool", "CronDeleteTool", "CronListTool")) {
            PermissionDecision decision = engine.evaluate(toolContext(toolName, args));
            assertEquals(PermissionEffect.ALLOW, decision.effect());
            assertEquals("builtin-cron-allow", decision.matchedRuleId());
        }
    }

    @Test
    void legacyCronToolShouldNotAllowByBaseline() {
        StubSettingsStore settings = new StubSettingsStore();
        SessionPermissionStore sessionStore = new SessionPermissionStore();
        PermissionEngine engine = new PermissionEngine(settings, sessionStore, new CommandRuleResolver(), new HardGuardService());

        PermissionDecision decision = engine.evaluate(toolContext("cron_tool", JsonNodeFactory.instance.objectNode()));

        assertEquals(PermissionEffect.ASK, decision.effect());
        assertEquals(ToolPolicyReasonCode.DEFAULT_REQUIRE_APPROVAL, decision.reasonCode());
    }

    private PermissionRule rule(String id, PermissionSource source, PermissionEffect effect) {
        return new PermissionRule(
                id,
                source,
                effect,
                "command_tool",
                "execute",
                PermissionResourceType.COMMAND,
                "",
                "",
                null,
                true
        );
    }

    private ToolPolicyContext commandContext(String conversationUid, String command) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("command", command);
        return new ToolPolicyContext(
                "command_tool",
                args,
                Path.of(".").toAbsolutePath().normalize(),
                "agent-uid",
                "default",
                "local",
                conversationUid,
                "m1",
                "s1"
        );
    }

    private ToolPolicyContext toolContext(String toolName, ObjectNode args) {
        return new ToolPolicyContext(
                toolName,
                args,
                Path.of(".").toAbsolutePath().normalize(),
                "agent-uid",
                "default",
                "local",
                "c11",
                "m1",
                "s1"
        );
    }

    private static final class StubSettingsStore extends PermissionSettingsStore {
        private List<PermissionRule> userRules = List.of();
        private List<PermissionRule> agentRules = List.of();

        @Override
        public List<PermissionRule> loadUserRules() {
            return userRules;
        }

        @Override
        public List<PermissionRule> loadAgentRules(String agentName) {
            return agentRules;
        }
    }
}
