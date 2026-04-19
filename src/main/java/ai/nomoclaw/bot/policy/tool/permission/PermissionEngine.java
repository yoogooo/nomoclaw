package ai.nomoclaw.bot.policy.tool.permission;

import ai.nomoclaw.bot.policy.tool.ToolPolicyContext;
import ai.nomoclaw.bot.policy.tool.ToolPolicyReasonCode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class PermissionEngine {

    private final PermissionSettingsStore settingsStore;
    private final SessionPermissionStore sessionPermissionStore;
    private final CommandRuleResolver commandRuleResolver;
    private final HardGuardService hardGuardService;

    public PermissionEngine(PermissionSettingsStore settingsStore,
                            SessionPermissionStore sessionPermissionStore,
                            CommandRuleResolver commandRuleResolver,
                            HardGuardService hardGuardService) {
        this.settingsStore = settingsStore;
        this.sessionPermissionStore = sessionPermissionStore;
        this.commandRuleResolver = commandRuleResolver;
        this.hardGuardService = hardGuardService;
    }

    public PermissionDecision evaluate(ToolPolicyContext context) {
        PermissionContextDetails details = commandRuleResolver.resolve(context);
        PermissionDecision hardDecision = hardGuardService.evaluate(details);
        if (hardDecision != null) {
            return hardDecision;
        }

        List<PermissionRule> sessionRules = sessionPermissionStore.listRules(context.conversationUid());
        List<PermissionRule> commandRules = commandRuleResolver.buildCommandRules(context, details);
        List<PermissionRule> agentRules = settingsStore.loadAgentRules(context.agentName());
        List<PermissionRule> userRules = settingsStore.loadUserRules();

        PermissionDecision decision = matchBySource(context, details, PermissionSource.SESSION, sessionRules);
        if (decision != null) {
            return decision;
        }
        decision = matchBySource(context, details, PermissionSource.COMMAND, commandRules);
        if (decision != null) {
            return decision;
        }
        decision = matchBySource(context, details, PermissionSource.AGENT_SETTINGS, agentRules);
        if (decision != null) {
            return decision;
        }
        decision = matchBySource(context, details, PermissionSource.USER_SETTINGS, userRules);
        if (decision != null) {
            return decision;
        }

        return new PermissionDecision(
                PermissionEffect.ASK,
                ToolPolicyReasonCode.DEFAULT_REQUIRE_APPROVAL,
                "未命中权限规则，需要手动确认。",
                null,
                "",
                firstPath(details),
                false
        );
    }

    public void persistRule(PermissionScope scope, String conversationUid, String agentName, PermissionRule rule) {
        if (scope == null || scope == PermissionScope.ONCE || rule == null) {
            return;
        }
        if (scope == PermissionScope.SESSION) {
            sessionPermissionStore.addRule(conversationUid, withSource(rule, PermissionSource.SESSION));
            return;
        }
        if (scope == PermissionScope.AGENT) {
            List<PermissionRule> existing = new ArrayList<>(settingsStore.loadAgentRules(agentName));
            existing.add(withSource(rule, PermissionSource.AGENT_SETTINGS));
            settingsStore.saveAgentRules(agentName, deduplicate(existing));
            return;
        }
        List<PermissionRule> existing = new ArrayList<>(settingsStore.loadUserRules());
        existing.add(withSource(rule, PermissionSource.USER_SETTINGS));
        settingsStore.saveUserRules(deduplicate(existing));
    }

    public List<PermissionRule> effectiveRules(String agentName, String conversationUid) {
        List<PermissionRule> all = new ArrayList<>();
        all.addAll(sessionPermissionStore.listRules(conversationUid));
        all.addAll(settingsStore.loadAgentRules(agentName));
        all.addAll(settingsStore.loadUserRules());
        return all;
    }

    private PermissionRule withSource(PermissionRule rule, PermissionSource source) {
        return new PermissionRule(
                rule.ruleId(),
                source,
                rule.effect(),
                rule.tool(),
                rule.action(),
                rule.resourceType(),
                rule.pathPattern(),
                rule.commandPattern(),
                rule.expiresAt(),
                rule.enabled()
        );
    }

    private PermissionDecision matchBySource(ToolPolicyContext context,
                                             PermissionContextDetails details,
                                             PermissionSource source,
                                             List<PermissionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        Instant now = Instant.now();
        List<PermissionRule> ordered = rules.stream()
                .filter(rule -> rule != null && rule.enabled() && !rule.isExpired(now))
                .sorted(Comparator.comparingInt(rule -> effectRank(rule.effect())))
                .toList();
        for (PermissionRule rule : ordered) {
            if (!matches(context, details, rule)) {
                continue;
            }
            return new PermissionDecision(
                    rule.effect(),
                    reasonCode(rule.effect()),
                    "命中权限规则。",
                    source,
                    rule.ruleId(),
                    firstPath(details),
                    false
            );
        }
        return null;
    }

    private boolean matches(ToolPolicyContext context, PermissionContextDetails details, PermissionRule rule) {
        String toolName = normalize(context.toolName());
        String ruleTool = normalize(rule.tool());
        if (!"*".equals(ruleTool) && !matchesTool(ruleTool, toolName)) {
            return false;
        }

        String ruleAction = normalize(rule.action());
        String actualAction = normalize(details.action());
        if (!ruleAction.isBlank() && !"*".equals(ruleAction) && !ruleAction.equals(actualAction)) {
            return false;
        }

        PermissionResourceType actualType = details.resourceType();
        if (rule.resourceType() != PermissionResourceType.ANY && actualType != PermissionResourceType.ANY && rule.resourceType() != actualType) {
            return false;
        }

        if (rule.pathPattern() != null && !rule.pathPattern().isBlank()) {
            List<Path> paths = details.resolvedPaths();
            if (paths == null || paths.isEmpty()) {
                return false;
            }
            boolean matched = paths.stream().anyMatch(path -> matchPathPattern(path, rule.pathPattern()));
            if (!matched) {
                return false;
            }
        }

        if (rule.commandPattern() != null && !rule.commandPattern().isBlank()) {
            String command = details.commandText() == null ? "" : details.commandText();
            try {
                if (!Pattern.compile(rule.commandPattern(), Pattern.CASE_INSENSITIVE).matcher(command).find()) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesTool(String ruleTool, String toolName) {
        if (ruleTool.endsWith("*")) {
            String prefix = ruleTool.substring(0, ruleTool.length() - 1);
            return toolName.startsWith(prefix);
        }
        if ("file_*".equals(ruleTool)) {
            return "file_tool".equals(toolName) || "file_io_tool".equals(toolName);
        }
        return ruleTool.equals(toolName);
    }

    private boolean matchPathPattern(Path path, String pattern) {
        Path normalized = path == null ? Path.of(".").toAbsolutePath().normalize() : path.toAbsolutePath().normalize();
        String absolute = normalized.toString();
        String normalizedPattern = pattern.trim();
        if (normalizedPattern.isBlank()) {
            return true;
        }
        normalizedPattern = normalizedPattern.replace("\\", "/");
        if (normalizedPattern.startsWith("~/")) {
            normalizedPattern = System.getProperty("user.home") + normalizedPattern.substring(1);
        }
        if (!normalizedPattern.contains("*") && !normalizedPattern.contains("?")) {
            Path base = Path.of(normalizedPattern).toAbsolutePath().normalize();
            return normalized.startsWith(base) || normalized.equals(base);
        }
        java.nio.file.PathMatcher matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
        return matcher.matches(Path.of(absolute));
    }

    private int effectRank(PermissionEffect effect) {
        if (effect == PermissionEffect.DENY) {
            return 0;
        }
        if (effect == PermissionEffect.ASK) {
            return 1;
        }
        return 2;
    }

    private ToolPolicyReasonCode reasonCode(PermissionEffect effect) {
        return switch (effect) {
            case DENY -> ToolPolicyReasonCode.RULE_DENY_MATCHED;
            case ASK -> ToolPolicyReasonCode.RULE_ASK_MATCHED;
            case ALLOW -> ToolPolicyReasonCode.RULE_ALLOW_MATCHED;
        };
    }

    private String firstPath(PermissionContextDetails details) {
        if (details == null || details.resolvedPaths() == null || details.resolvedPaths().isEmpty()) {
            return "";
        }
        Path path = details.resolvedPaths().get(0);
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
    }

    private List<PermissionRule> deduplicate(List<PermissionRule> rules) {
        java.util.LinkedHashMap<String, PermissionRule> out = new java.util.LinkedHashMap<>();
        for (PermissionRule rule : rules) {
            if (rule == null) {
                continue;
            }
            String key = normalize(rule.tool()) + "|" + normalize(rule.action()) + "|" + rule.effect() + "|"
                    + normalize(rule.pathPattern()) + "|" + normalize(rule.commandPattern()) + "|" + rule.resourceType();
            out.put(key, rule);
        }
        return List.copyOf(out.values());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
