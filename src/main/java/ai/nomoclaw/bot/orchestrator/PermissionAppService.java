package ai.nomoclaw.bot.orchestrator;
import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEffect;
import ai.nomoclaw.bot.policy.tool.permission.PermissionResourceType;
import ai.nomoclaw.bot.policy.tool.permission.PermissionRule;
import ai.nomoclaw.bot.policy.tool.permission.PermissionScope;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSettingsStore;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSource;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PermissionAppService {

    private static final List<String> HARD_GUARD_PROTECTED = List.of(".git", ".nomoclaw", ".vscode");
    private static final List<String> HARD_GUARD_SYSTEM = List.of("/etc", "/usr", "/bin", "/sbin", "/var", "/System", "/Library", "/private", "/opt", "/boot", "/dev", "/proc");

    private final ToolPermissionPolicyService toolPermissionPolicyService;
    private final PermissionSettingsStore settingsStore;
    private final AgentDefinitionRepository agentDefinitionRepository;

    public PermissionAppService(ToolPermissionPolicyService toolPermissionPolicyService,
                                PermissionSettingsStore settingsStore,
                                AgentDefinitionRepository agentDefinitionRepository) {
        this.toolPermissionPolicyService = toolPermissionPolicyService;
        this.settingsStore = settingsStore;
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    public PermissionRulesResponse getEffectiveRules(String conversationUid, String agentUid) {
        AgentDefinitionEntity agent = resolveAgent(agentUid);
        String agentName = agent == null ? NomoClawPaths.DEFAULT_AGENT_NAME : agent.getAgentName();
        String resolvedAgentUid = agent == null ? "" : agent.getAgentUid();
        List<PermissionRule> effective = toolPermissionPolicyService.effectiveRules(agentName, conversationUid == null ? "" : conversationUid);

        List<PermissionRulePayload> sessionRules = new ArrayList<>();
        List<PermissionRulePayload> agentRules = new ArrayList<>();
        List<PermissionRulePayload> userRules = new ArrayList<>();
        for (PermissionRule rule : effective) {
            if (rule.source() == PermissionSource.SESSION) {
                sessionRules.add(toPayload(rule));
            } else if (rule.source() == PermissionSource.AGENT_SETTINGS) {
                agentRules.add(toPayload(rule));
            } else if (rule.source() == PermissionSource.USER_SETTINGS) {
                userRules.add(toPayload(rule));
            }
        }

        return new PermissionRulesResponse(
                resolvedAgentUid,
                agentName,
                sessionRules,
                List.of(),
                agentRules,
                userRules,
                HARD_GUARD_PROTECTED,
                HARD_GUARD_SYSTEM
        );
    }

    public PermissionRulesResponse updateAgentRules(String agentUid, UpdatePermissionRulesRequest request) {
        AgentDefinitionEntity agent = resolveAgent(agentUid);
        if (agent == null) {
            throw new IllegalArgumentException("agent not found: " + agentUid);
        }
        List<PermissionRule> rules = parseRules(request, PermissionSource.AGENT_SETTINGS);
        settingsStore.saveAgentRules(agent.getAgentName(), rules);
        return getEffectiveRules("", agentUid);
    }

    public PermissionRulesResponse updateUserRules(String agentUid, UpdatePermissionRulesRequest request) {
        List<PermissionRule> rules = parseRules(request, PermissionSource.USER_SETTINGS);
        settingsStore.saveUserRules(rules);
        return getEffectiveRules("", agentUid);
    }

    public PermissionRule ruleFromApproval(String toolName, tools.jackson.databind.JsonNode toolArgs, PermissionEffect effect, PermissionSource source) {
        String action = toolArgs == null ? "*" : toolArgs.path("action").asText("*");
        String path = toolArgs == null ? "" : toolArgs.path("path").asText("");
        String command = toolArgs == null ? "" : toolArgs.path("command").asText("");
        return new PermissionRule(
                UUID.randomUUID().toString(),
                source,
                effect,
                toolName == null || toolName.isBlank() ? "*" : toolName,
                action == null || action.isBlank() ? "*" : action,
                PermissionResourceType.fromTool(toolName),
                path == null ? "" : path,
                command.isBlank() ? "" : command,
                null,
                true
        );
    }

    private List<PermissionRule> parseRules(UpdatePermissionRulesRequest request, PermissionSource source) {
        if (request == null || request.rules() == null) {
            return List.of();
        }
        List<PermissionRule> out = new ArrayList<>();
        for (PermissionRulePayload payload : request.rules()) {
            if (payload == null) {
                continue;
            }
            PermissionEffect effect = parseEffect(payload.effect());
            if (effect == null) {
                continue;
            }
            Instant expiresAt = null;
            String expiresAtRaw = payload.expiresAt() == null ? "" : payload.expiresAt().trim();
            if (!expiresAtRaw.isBlank()) {
                try {
                    expiresAt = Instant.parse(expiresAtRaw);
                } catch (Exception ignored) {
                    expiresAt = null;
                }
            }
            out.add(new PermissionRule(
                    payload.ruleId() == null || payload.ruleId().isBlank() ? UUID.randomUUID().toString() : payload.ruleId().trim(),
                    source,
                    effect,
                    payload.tool() == null || payload.tool().isBlank() ? "*" : payload.tool().trim(),
                    payload.action() == null || payload.action().isBlank() ? "*" : payload.action().trim(),
                    PermissionResourceType.from(payload.resourceType()),
                    payload.pathPattern() == null ? "" : payload.pathPattern().trim(),
                    payload.commandPattern() == null ? "" : payload.commandPattern().trim(),
                    expiresAt,
                    payload.enabled() == null || payload.enabled()
            ));
        }
        return out;
    }

    private PermissionRulePayload toPayload(PermissionRule rule) {
        return new PermissionRulePayload(
                rule.ruleId(),
                rule.effect().name(),
                rule.tool(),
                rule.action(),
                rule.resourceType().name(),
                rule.pathPattern(),
                rule.commandPattern(),
                rule.expiresAt() == null ? "" : rule.expiresAt().toString(),
                rule.enabled()
        );
    }

    private PermissionEffect parseEffect(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PermissionEffect.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private AgentDefinitionEntity resolveAgent(String agentUid) {
        if (agentUid == null || agentUid.isBlank()) {
            return null;
        }
        return agentDefinitionRepository.findByUid(agentUid.trim());
    }
}
