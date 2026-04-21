package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.api.PermissionRulePayload;
import ai.nomoclaw.bot.api.PermissionRulesResponse;
import ai.nomoclaw.bot.api.UpdatePermissionRulesRequest;
import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.policy.tool.permission.*;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.tool.PathResolver;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class PermissionAppService {

    private static final String MANAGED_WORKSPACE_RULE_PREFIX = "managed-agent-workspace-";
    private static final Set<String> COMMAND_KEYWORDS = Set.of(
            "bash", "sh", "zsh", "python", "node", "ruby", "perl", "env", "command", "git", "clone",
            "mkdir", "cp", "mv", "rm", "chmod", "chown", "touch", "ln", "sed", "tee", "tar", "unzip",
            "rsync", "cat", "echo", "install", "find", "xargs", "head", "tail", "ls", "grep", "awk", "sort", "uniq", "wc"
    );

    private final ToolPermissionPolicyService toolPermissionPolicyService;
    private final PermissionSettingsStore settingsStore;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final HardGuardService hardGuardService;

    public PermissionAppService(ToolPermissionPolicyService toolPermissionPolicyService,
                                PermissionSettingsStore settingsStore,
                                AgentDefinitionRepository agentDefinitionRepository,
                                HardGuardService hardGuardService) {
        this.toolPermissionPolicyService = toolPermissionPolicyService;
        this.settingsStore = settingsStore;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.hardGuardService = hardGuardService;
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
                hardGuardService.protectedNames(),
                hardGuardService.systemRoots()
        );
    }

    public PermissionRulesResponse updateAgentRules(String agentUid, UpdatePermissionRulesRequest request) {
        AgentDefinitionEntity agent = resolveAgent(agentUid);
        if (agent == null) {
            throw new IllegalArgumentException("agent not found: " + agentUid);
        }
        List<PermissionRule> rules = parseRules(request, PermissionSource.AGENT_SETTINGS);
        settingsStore.saveAgentRules(agent.getAgentName(), rules);
        AgentWorkspaceConfig workspaceConfig = AgentWorkspaceConfig.resolve(agent.getAgentName(), agent.getWorkspace());
        syncAgentManagedWorkspaceAllowRule(
                agent.getAgentUid(),
                agent.getAgentName(),
                workspaceConfig.workspaceDir()
        );
        return getEffectiveRules("", agentUid);
    }

    public PermissionRulesResponse updateUserRules(String agentUid, UpdatePermissionRulesRequest request) {
        List<PermissionRule> rules = parseRules(request, PermissionSource.USER_SETTINGS);
        settingsStore.saveUserRules(rules);
        return getEffectiveRules("", agentUid);
    }

    public PermissionRule ruleFromApproval(String toolName,
                                           tools.jackson.databind.JsonNode toolArgs,
                                           PermissionEffect effect,
                                           PermissionSource source,
                                           Path workspacePath) {
        String action = toolArgs == null ? "*" : toolArgs.path("action").asText("*");
        String path = toolArgs == null ? "" : toolArgs.path("path").asText("");
        String command = toolArgs == null ? "" : toolArgs.path("command").asText("");
        String normalizedTool = toolName == null || toolName.isBlank() ? "*" : toolName;
        String pathPattern = path == null ? "" : path;
        String commandPattern = command.isBlank() ? "" : command;
        if ("command_tool".equalsIgnoreCase(normalizedTool)) {
            pathPattern = resolveCommandScopePath(
                    command,
                    toolArgs == null ? "" : toolArgs.path("cwd").asText(""),
                    workspacePath
            );
            commandPattern = "";
        }
        return new PermissionRule(
                UUID.randomUUID().toString(),
                source,
                effect,
                normalizedTool,
                action == null || action.isBlank() ? "*" : action,
                PermissionResourceType.fromTool(toolName),
                pathPattern == null ? "" : pathPattern,
                commandPattern == null ? "" : commandPattern,
                null,
                true
        );
    }

    public void syncAgentManagedWorkspaceAllowRule(String agentUid,
                                                   String agentName,
                                                   Path workspacePath) {
        String normalizedAgentName = agentName == null ? "" : agentName.trim();
        if (normalizedAgentName.isBlank()) {
            return;
        }
        Path workspace = (workspacePath == null
                ? NomoClawPaths.agentHome(normalizedAgentName).resolve("workspace")
                : workspacePath).toAbsolutePath().normalize();
        List<PermissionRule> existing = new ArrayList<>(settingsStore.loadAgentRules(normalizedAgentName));
        String key = managedRuleKey(agentUid, normalizedAgentName);
        existing.removeIf(rule -> isManagedRule(rule, key));
        existing.add(new PermissionRule(
                managedRuleId(key, "file", 0),
                PermissionSource.AGENT_SETTINGS,
                PermissionEffect.ALLOW,
                "file_*",
                "*",
                PermissionResourceType.FILE,
                workspace.toString(),
                "",
                null,
                true
        ));
        existing.add(new PermissionRule(
                managedRuleId(key, "command", 0),
                PermissionSource.AGENT_SETTINGS,
                PermissionEffect.ALLOW,
                "command_tool",
                "execute",
                PermissionResourceType.COMMAND,
                workspace.toString(),
                "",
                null,
                true
        ));
        settingsStore.saveAgentRules(normalizedAgentName, existing);
    }

    public void removeAgentManagedWorkspaceRules(String agentUid, String agentName) {
        String normalizedAgentName = agentName == null ? "" : agentName.trim();
        if (normalizedAgentName.isBlank()) {
            return;
        }
        String key = managedRuleKey(agentUid, normalizedAgentName);
        List<PermissionRule> existing = new ArrayList<>(settingsStore.loadAgentRules(normalizedAgentName));
        boolean changed = existing.removeIf(rule -> isManagedRule(rule, key));
        if (changed) {
            settingsStore.saveAgentRules(normalizedAgentName, existing);
        }
    }

    private String resolveCommandScopePath(String command, String cwdRaw, Path workspacePath) {
        if (command == null || command.isBlank()) {
            return normalizeCwd(cwdRaw, workspacePath);
        }
        Path cwd = normalizePath(cwdRaw, workspacePath);
        List<Path> candidates = new ArrayList<>();
        for (String token : tokenize(command)) {
            String cleaned = stripQuotes(token);
            if (cleaned.isBlank() || cleaned.startsWith("-") || isShellOperator(cleaned) || cleaned.contains("=") || looksLikeUrl(cleaned)) {
                continue;
            }
            if (COMMAND_KEYWORDS.contains(cleaned.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (cleaned.contains("/") || cleaned.startsWith(".") || cleaned.startsWith("~")) {
                Path resolved = PathResolver.resolve(cleaned, cwd);
                candidates.add(toDirectoryScope(resolved));
            }
        }
        if (candidates.isEmpty()) {
            return cwd.toString();
        }
        Path common = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            common = commonPrefix(common, candidates.get(i));
            if (common == null) {
                break;
            }
        }
        return (common == null ? cwd : common).toString();
    }

    private Path normalizePath(String cwdRaw, Path workspacePath) {
        Path workspace = workspacePath == null
                ? Path.of(".").toAbsolutePath().normalize()
                : workspacePath.toAbsolutePath().normalize();
        if (cwdRaw == null || cwdRaw.isBlank()) {
            return workspace;
        }
        return PathResolver.resolve(cwdRaw, workspace);
    }

    private String normalizeCwd(String cwdRaw, Path workspacePath) {
        return normalizePath(cwdRaw, workspacePath).toString();
    }

    private Path toDirectoryScope(Path path) {
        if (path == null) {
            return Path.of(".").toAbsolutePath().normalize();
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.contains(".") || name.contains("*") || name.contains("?")) {
            Path parent = path.getParent();
            return parent == null ? path : parent;
        }
        return path;
    }

    private Path commonPrefix(Path a, Path b) {
        if (a == null || b == null) {
            return null;
        }
        Path left = a.toAbsolutePath().normalize();
        Path right = b.toAbsolutePath().normalize();
        while (left != null) {
            if (right.startsWith(left)) {
                return left;
            }
            left = left.getParent();
        }
        return null;
    }

    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher matcher = Pattern.compile("\"([^\"]*)\"|'([^']*)'|\\S+").matcher(command);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private boolean isShellOperator(String token) {
        return "|".equals(token) || "&&".equals(token) || "||".equals(token) || ";".equals(token);
    }

    private boolean looksLikeUrl(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("git@");
    }

    private String stripQuotes(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String out = token.trim();
        if ((out.startsWith("\"") && out.endsWith("\"")) || (out.startsWith("'") && out.endsWith("'"))) {
            out = out.substring(1, out.length() - 1);
        }
        return out.trim();
    }

    private String managedRuleKey(String agentUid, String agentName) {
        String normalizedAgentUid = agentUid == null ? "" : agentUid.trim();
        if (!normalizedAgentUid.isBlank()) {
            return normalizedAgentUid;
        }
        return "name-" + agentName.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String managedRuleId(String key, String category, int index) {
        return MANAGED_WORKSPACE_RULE_PREFIX + key + "-" + category + "-" + index;
    }

    private boolean isManagedRule(PermissionRule rule, String key) {
        if (rule == null || rule.ruleId() == null) {
            return false;
        }
        String prefix = MANAGED_WORKSPACE_RULE_PREFIX + key + "-";
        return rule.ruleId().startsWith(prefix);
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
