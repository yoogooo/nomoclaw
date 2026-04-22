package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.policy.tool.permission.HardGuardService;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEffect;
import ai.nomoclaw.bot.policy.tool.permission.PermissionResourceType;
import ai.nomoclaw.bot.policy.tool.permission.PermissionRule;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSettingsStore;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSource;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PermissionAppServiceTests {

    @Test
    void syncManagedWorkspaceShouldAddSkillsRootReadonlyAllowRules() {
        InMemoryPermissionSettingsStore settingsStore = new InMemoryPermissionSettingsStore();
        settingsStore.agentRules = new ArrayList<>(List.of(
                new PermissionRule(
                        "custom-keep-rule",
                        PermissionSource.AGENT_SETTINGS,
                        PermissionEffect.ALLOW,
                        "current_time_tool",
                        "*",
                        PermissionResourceType.ANY,
                        "",
                        "",
                        null,
                        true
                ),
                new PermissionRule(
                        "managed-agent-workspace-agent_general_assistant-old-0",
                        PermissionSource.AGENT_SETTINGS,
                        PermissionEffect.ALLOW,
                        "file_*",
                        "*",
                        PermissionResourceType.FILE,
                        "/tmp/old",
                        "",
                        null,
                        true
                )
        ));

        PermissionAppService service = new PermissionAppService(
                mock(ToolPermissionPolicyService.class),
                settingsStore,
                mock(AgentDefinitionRepository.class),
                new HardGuardService()
        );

        Path workspace = Path.of("/tmp/managed-workspace").toAbsolutePath().normalize();
        service.syncAgentManagedWorkspaceAllowRule("agent_general_assistant", "general_assistant", workspace);

        List<PermissionRule> rules = settingsStore.loadAgentRules("general_assistant");
        String skillsRoot = NomoClawPaths.skillsRoot().toAbsolutePath().normalize().toString();

        assertTrue(hasRule(rules, "managed-agent-workspace-agent_general_assistant-file-0", "file_*", "*", workspace.toString()));
        assertTrue(hasRule(rules, "managed-agent-workspace-agent_general_assistant-command-0", "command_tool", "execute", workspace.toString()));
        assertTrue(hasRule(rules, "managed-agent-workspace-agent_general_assistant-skills-read-0", "file_*", "read", skillsRoot));
        assertTrue(hasRule(rules, "managed-agent-workspace-agent_general_assistant-skills-list-0", "file_*", "list", skillsRoot));

        assertTrue(rules.stream().anyMatch(rule -> "custom-keep-rule".equals(rule.ruleId())));
        long managedRuleCount = rules.stream()
                .filter(rule -> rule.ruleId().startsWith("managed-agent-workspace-agent_general_assistant-"))
                .count();
        assertEquals(4, managedRuleCount);
    }

    private boolean hasRule(List<PermissionRule> rules,
                            String ruleId,
                            String tool,
                            String action,
                            String pathPattern) {
        return rules.stream().anyMatch(rule ->
                ruleId.equals(rule.ruleId())
                        && tool.equals(rule.tool())
                        && action.equals(rule.action())
                        && pathPattern.equals(rule.pathPattern())
                        && rule.effect() == PermissionEffect.ALLOW
                        && rule.source() == PermissionSource.AGENT_SETTINGS
        );
    }

    private static final class InMemoryPermissionSettingsStore extends PermissionSettingsStore {
        private List<PermissionRule> agentRules = new ArrayList<>();

        @Override
        public List<PermissionRule> loadAgentRules(String agentName) {
            return List.copyOf(agentRules);
        }

        @Override
        public synchronized void saveAgentRules(String agentName, List<PermissionRule> rules) {
            this.agentRules = new ArrayList<>(rules == null ? List.of() : rules);
        }
    }
}
