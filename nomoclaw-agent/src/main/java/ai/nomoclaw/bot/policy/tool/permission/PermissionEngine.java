package ai.nomoclaw.bot.policy.tool.permission;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.policy.tool.ToolPolicyContext;
import ai.nomoclaw.bot.policy.tool.ToolPolicyReasonCode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PermissionEngine {
    private static final Set<String> BUILTIN_READONLY_TOOLS = Set.of(
            "memorysearchtool",
            "currenttimetool",
            "tokenusagetool",
            "readfiletool",
            "filesearchtool",
            "greptool",
            "imageloadertool",
            "websearchtool",
            "webfetchtool"
    );

    private final PermissionSettingsStore settingsStore;
    private final SessionPermissionStore sessionPermissionStore;
    private final CommandRuleResolver commandRuleResolver;
    private final HardGuardService hardGuardService;
    private final AgentProperties agentProperties;

    public PermissionEngine(PermissionSettingsStore settingsStore,
                            SessionPermissionStore sessionPermissionStore,
                            CommandRuleResolver commandRuleResolver,
                            HardGuardService hardGuardService,
                            AgentProperties agentProperties) {
        this.settingsStore = settingsStore;
        this.sessionPermissionStore = sessionPermissionStore;
        this.commandRuleResolver = commandRuleResolver;
        this.hardGuardService = hardGuardService;
        this.agentProperties = agentProperties;
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

        Map<PermissionSource, List<PermissionRule>> rulesBySource = new LinkedHashMap<>();
        rulesBySource.put(PermissionSource.SESSION, sessionRules);
        rulesBySource.put(PermissionSource.COMMAND, commandRules);
        rulesBySource.put(PermissionSource.AGENT_SETTINGS, agentRules);
        rulesBySource.put(PermissionSource.USER_SETTINGS, userRules);

        PermissionDecision decision = matchByBehavior(context, details, PermissionEffect.DENY, rulesBySource);
        if (decision != null) {
            return decision;
        }
        decision = matchByBehavior(context, details, PermissionEffect.ASK, rulesBySource);
        if (decision != null) {
            return decision;
        }
        PermissionDecision browserLocalBridgeGate = evaluateBrowserLocalBridgeConsent(context, details, rulesBySource);
        if (browserLocalBridgeGate != null) {
            return browserLocalBridgeGate;
        }
        decision = matchByBehavior(context, details, PermissionEffect.ALLOW, rulesBySource);
        if (decision != null) {
            return decision;
        }

        if (isCommandReadonlyBaselineAllowed(context, details)) {
            return new PermissionDecision(
                    PermissionEffect.ALLOW,
                    ToolPolicyReasonCode.RULE_ALLOW_MATCHED,
                    "命中只读命令基线放行。",
                    PermissionSource.COMMAND,
                    "builtin-command-readonly-allow",
                    firstPath(details),
                    false
            );
        }

        if (isBrowserBaselineAllowed(context)) {
            return new PermissionDecision(
                    PermissionEffect.ALLOW,
                    ToolPolicyReasonCode.RULE_ALLOW_MATCHED,
                    "命中浏览器工具基线放行。",
                    PermissionSource.COMMAND,
                    "builtin-browser-open-allow",
                    firstPath(details),
                    false
            );
        }

        if (isCronBaselineAllowed(context)) {
            return new PermissionDecision(
                    PermissionEffect.ALLOW,
                    ToolPolicyReasonCode.RULE_ALLOW_MATCHED,
                    "命中定时任务基线放行。",
                    PermissionSource.COMMAND,
                    "builtin-cron-allow",
                    firstPath(details),
                    false
            );
        }

        if (isMcpBaselineAllowed(context)) {
            return new PermissionDecision(
                    PermissionEffect.ALLOW,
                    ToolPolicyReasonCode.RULE_ALLOW_MATCHED,
                    "MCP 工具默认放行。",
                    PermissionSource.COMMAND,
                    "mcp-default-allow",
                    firstPath(details),
                    false
            );
        }

        if (isBuiltinReadonlyTool(context.toolName())) {
            return new PermissionDecision(
                    PermissionEffect.ALLOW,
                    ToolPolicyReasonCode.RULE_ALLOW_MATCHED,
                    "内置只读工具默认放行。",
                    PermissionSource.COMMAND,
                    "builtin-readonly-allow",
                    firstPath(details),
                    false
            );
        }
        if (isCreateFileNewTargetAllowed(context, details)) {
            return new PermissionDecision(
                    PermissionEffect.ALLOW,
                    ToolPolicyReasonCode.RULE_ALLOW_MATCHED,
                    "新建文件写入默认放行。",
                    PermissionSource.COMMAND,
                    "builtin-file-create-new-allow",
                    firstPath(details),
                    false
            );
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

    private PermissionDecision matchByBehavior(ToolPolicyContext context,
                                               PermissionContextDetails details,
                                               PermissionEffect behavior,
                                               Map<PermissionSource, List<PermissionRule>> rulesBySource) {
        Instant now = Instant.now();
        for (Map.Entry<PermissionSource, List<PermissionRule>> entry : rulesBySource.entrySet()) {
            PermissionSource source = entry.getKey();
            List<PermissionRule> rules = entry.getValue();
            if (rules == null || rules.isEmpty()) {
                continue;
            }
            for (PermissionRule rule : rules) {
                if (rule == null || !rule.enabled() || rule.isExpired(now) || rule.effect() != behavior) {
                    continue;
                }
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
            return "readfiletool".equals(toolName)
                    || "listfiletool".equals(toolName)
                    || "createfiletool".equals(toolName)
                    || "editfiletool".equals(toolName);
        }
        return ruleTool.equals(toolName);
    }

    private boolean matchPathPattern(Path path, String pattern) {
        if (path == null) {
            return false;
        }
        List<Path> candidates = pathCandidates(path);
        String normalizedPattern = pattern.trim();
        if (normalizedPattern.isBlank()) {
            return true;
        }
        normalizedPattern = expandHome(normalizedPattern).replace("\\", "/");
        boolean insensitive = isCaseInsensitiveFs();
        if (normalizedPattern.startsWith("~/")) {
            normalizedPattern = System.getProperty("user.home") + normalizedPattern.substring(1);
        }
        if (!normalizedPattern.contains("*") && !normalizedPattern.contains("?")) {
            Path base = Path.of(normalizedPattern).toAbsolutePath().normalize();
            for (Path candidate : candidates) {
                if (startsWithPath(candidate, base, insensitive) || equalsPath(candidate, base, insensitive)) {
                    return true;
                }
            }
            return false;
        }
        java.nio.file.PathMatcher matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (matcher.matches(Paths.get(normalized.toString()))) {
                return true;
            }
            if (insensitive && matcher.matches(Paths.get(normalized.toString().toLowerCase(Locale.ROOT)))) {
                return true;
            }
        }
        return false;
    }

    private List<Path> pathCandidates(Path path) {
        List<Path> out = new ArrayList<>();
        Path normalized = path.toAbsolutePath().normalize();
        out.add(normalized);
        try {
            if (Files.exists(normalized)) {
                Path real = normalized.toRealPath().normalize();
                if (!real.equals(normalized)) {
                    out.add(real);
                }
            }
        } catch (Exception ignored) {
            // ignore invalid path resolution and fallback to normalized only
        }
        return out;
    }

    private boolean isCaseInsensitiveFs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") || os.contains("mac");
    }

    private String expandHome(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String home = System.getProperty("user.home");
        if ("~".equals(value)) {
            return home;
        }
        if (value.startsWith("~/")) {
            return home + value.substring(1);
        }
        return value;
    }

    private boolean equalsPath(Path left, Path right, boolean insensitive) {
        if (!insensitive) {
            return left.equals(right);
        }
        return left.toString().toLowerCase(Locale.ROOT).equals(right.toString().toLowerCase(Locale.ROOT));
    }

    private boolean startsWithPath(Path value, Path base, boolean insensitive) {
        if (!insensitive) {
            return value.startsWith(base);
        }
        String valueText = value.toString().toLowerCase(Locale.ROOT);
        String baseText = base.toString().toLowerCase(Locale.ROOT);
        return valueText.equals(baseText) || valueText.startsWith(baseText + "/") || valueText.startsWith(baseText + "\\");
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

    private boolean isBuiltinReadonlyTool(String toolName) {
        return BUILTIN_READONLY_TOOLS.contains(normalize(toolName));
    }

    private boolean isCommandReadonlyBaselineAllowed(ToolPolicyContext context, PermissionContextDetails details) {
        String tool = normalize(context.toolName());
        if (!"commandtool".equals(tool)) {
            return false;
        }
        return commandRuleResolver.isReadonlyParam(context, details) == CommandRuleResolver.ReadonlyCommandVerdict.READ_ONLY;
    }

    private boolean isBrowserBaselineAllowed(ToolPolicyContext context) {
        String tool = normalize(context.toolName());
        return "browsertool".equals(tool);
    }

    private boolean isCronBaselineAllowed(ToolPolicyContext context) {
        String tool = normalize(context.toolName());
        return "croncreatetool".equals(tool)
                || "crondeletetool".equals(tool)
                || "cronlisttool".equals(tool);
    }

    private boolean isMcpBaselineAllowed(ToolPolicyContext context) {
        return normalize(context.toolName()).startsWith("mcp_");
    }

    private boolean isCreateFileNewTargetAllowed(ToolPolicyContext context, PermissionContextDetails details) {
        if (!"createfiletool".equals(normalize(context.toolName()))) {
            return false;
        }
        String mode = context.toolArgs() == null ? "" : context.toolArgs().path("mode").asString("create_or_truncate");
        if (!"create_or_truncate".equalsIgnoreCase(mode)) {
            return false;
        }
        if (details == null || details.resolvedPaths() == null || details.resolvedPaths().isEmpty()) {
            return false;
        }
        Path target = details.resolvedPaths().get(0);
        if (target == null) {
            return false;
        }
        return !Files.exists(target.toAbsolutePath().normalize());
    }

    private PermissionDecision evaluateBrowserLocalBridgeConsent(ToolPolicyContext context,
                                                                 PermissionContextDetails details,
                                                                 Map<PermissionSource, List<PermissionRule>> rulesBySource) {
        if (!requiresLocalBridgeConsent(context)) {
            return null;
        }
        PermissionDecision allowDecision = matchByBehavior(context, details, PermissionEffect.ALLOW, rulesBySource);
        if (allowDecision != null) {
            return allowDecision;
        }
        return new PermissionDecision(
                PermissionEffect.ASK,
                ToolPolicyReasonCode.POLICY_REQUIRE_APPROVAL_HIGH_RISK,
                "local bridge 复用本地登录态访问高风险域名，需要用户确认。",
                null,
                "",
                firstPath(details),
                false
        );
    }

    private boolean requiresLocalBridgeConsent(ToolPolicyContext context) {
        if (!agentProperties.getBrowser().getLocalBridge().isConsentRequired()) {
            return false;
        }
        if (!"browsertool".equals(normalize(context.toolName()))) {
            return false;
        }
        String action = context.toolArgs().path("action").asString("").trim().toLowerCase(Locale.ROOT);
        if (!"open".equals(action) && !"navigate".equals(action)) {
            return false;
        }
        String mode = agentProperties.getBrowser().getMode() == null
                ? "auto"
                : agentProperties.getBrowser().getMode().trim().toLowerCase(Locale.ROOT);
        if ("managed".equals(mode)) {
            return false;
        }
        if ("local_bridge".equals(mode)) {
            return true;
        }
        if (!"auto".equals(mode)) {
            return false;
        }
        String host = BrowserPermissionSupport.extractHost(context.toolArgs().path("url").asString(""));
        return BrowserPermissionSupport.matchesDomain(host, agentProperties.getBrowser().getLocalBridge().getLocalBridgeDomains());
    }
}
