package ai.nomoclaw.bot.policy.tool;

import ai.nomoclaw.bot.policy.tool.config.ToolPolicyConfig;
import ai.nomoclaw.bot.policy.tool.config.ToolPolicyConfigProvider;
import ai.nomoclaw.bot.tool.PathResolver;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ToolPermissionPolicyService {

    private static final Set<String> WRITE_FILE_ACTIONS = Set.of("write", "append", "edit");
    private static final Set<String> PROTECTED_ROOTS = Set.of(
            "/etc", "/usr", "/bin", "/sbin", "/var", "/System", "/Library", "/private", "/opt", "/boot", "/dev", "/proc"
    );

    private final ToolPolicyConfigProvider configProvider;

    public ToolPermissionPolicyService(ToolPolicyConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    public ToolPolicyDecisionResult evaluate(ToolPolicyContext context) {
        String toolName = normalize(context.toolName());
        if (!"command_tool".equals(toolName) && !"file_tool".equals(toolName) && !"file_io_tool".equals(toolName)) {
            return ToolPolicyDecisionResult.allow();
        }

        ToolPolicyConfig config = configProvider.current();
        Path workspace = normalizePath(context.agentWorkspacePath() == null
                ? NomoClawPaths.agentWorkspace(NomoClawPaths.DEFAULT_AGENT_NAME)
                : context.agentWorkspacePath());

        if ("command_tool".equals(toolName)) {
            return evaluateCommandPolicy(context, workspace, config);
        }
        return evaluateFilePolicy(context, workspace, config);
    }

    private ToolPolicyDecisionResult evaluateFilePolicy(ToolPolicyContext context, Path workspace, ToolPolicyConfig config) {
        String action = normalize(context.toolArgs().path("action").asText(""));
        if (action.isBlank()) {
            return ToolPolicyDecisionResult.allow();
        }

        if (config.highRiskFileActions().contains(action)) {
            return ToolPolicyDecisionResult.ask(
                    ToolPolicyReasonCode.POLICY_REQUIRE_APPROVAL_HIGH_RISK,
                    "该文件操作为高风险动作，需要先确认。",
                    action
            );
        }

        if (!WRITE_FILE_ACTIONS.contains(action)) {
            return ToolPolicyDecisionResult.allow();
        }

        String pathRaw = context.toolArgs().path("path").asText("");
        Path target = resolvePath(pathRaw, workspace);
        return evaluateWritablePath(target, workspace, config, "文件写入路径");
    }

    private ToolPolicyDecisionResult evaluateCommandPolicy(ToolPolicyContext context, Path workspace, ToolPolicyConfig config) {
        String command = context.toolArgs().path("command").asText("");
        if (command == null || command.isBlank()) {
            return ToolPolicyDecisionResult.allow();
        }

        boolean highRiskCommand = matchesAnyPattern(command, config.highRiskCommandPatterns());
        if (!hasWriteIntent(command)) {
            if (highRiskCommand) {
                return ToolPolicyDecisionResult.ask(
                        ToolPolicyReasonCode.POLICY_REQUIRE_APPROVAL_HIGH_RISK,
                        "该命令属于高风险操作，需要先确认。",
                        summarizeCommand(command)
                );
            }
            return ToolPolicyDecisionResult.allow();
        }

        Path commandCwd = resolveCommandCwd(context, workspace);
        List<Path> writeTargets = extractCommandWriteTargets(command, commandCwd);
        if (writeTargets.isEmpty()) {
            return ToolPolicyDecisionResult.ask(
                    ToolPolicyReasonCode.POLICY_REQUIRE_APPROVAL_HIGH_RISK,
                    "检测到可能改写文件的命令，但无法可靠识别目标路径，需要先确认。",
                    summarizeCommand(command)
            );
        }

        for (Path target : writeTargets) {
            ToolPolicyDecisionResult decision = evaluateWritablePath(target, workspace, config, "命令写入路径");
            if (decision.decision() != ToolPolicyDecision.ALLOW) {
                return decision;
            }
        }

        if (highRiskCommand) {
            return ToolPolicyDecisionResult.ask(
                    ToolPolicyReasonCode.POLICY_REQUIRE_APPROVAL_HIGH_RISK,
                    "该命令属于高风险操作，需要先确认。",
                    summarizePaths(writeTargets)
            );
        }
        return ToolPolicyDecisionResult.allow();
    }

    private ToolPolicyDecisionResult evaluateWritablePath(Path target,
                                                          Path workspace,
                                                          ToolPolicyConfig config,
                                                          String pathLabel) {
        Path normalized = normalizePath(target);
        String summary = pathLabel + "=" + normalized;

        if (isProtectedSystemPath(normalized, workspace)) {
            return ToolPolicyDecisionResult.deny(
                    ToolPolicyReasonCode.POLICY_BLOCKED_SYSTEM_PATH_WRITE,
                    "禁止修改系统关键目录。",
                    summary
            );
        }

        if (matchesPathRules(normalized, config.denyWritePaths())) {
            return ToolPolicyDecisionResult.deny(
                    ToolPolicyReasonCode.POLICY_BLOCKED_BY_DENY_RULE,
                    "该路径被系统 deny 规则禁止写入。",
                    summary
            );
        }

        if (isUnder(workspace, normalized) || matchesPathRules(normalized, config.allowWritePaths())) {
            return ToolPolicyDecisionResult.allow();
        }

        return ToolPolicyDecisionResult.deny(
                ToolPolicyReasonCode.POLICY_WRITE_OUTSIDE_ALLOWED_SCOPE,
                "仅允许写入 Agent 工作目录或 settings.json allowWritePaths 配置的路径。",
                summary
        );
    }

    private Path resolveCommandCwd(ToolPolicyContext context, Path workspace) {
        String cwdRaw = context.toolArgs().path("cwd").asText("");
        if (cwdRaw == null || cwdRaw.isBlank()) {
            return workspace;
        }
        return normalizePath(PathResolver.resolve(cwdRaw, workspace));
    }

    private List<Path> extractCommandWriteTargets(String command, Path cwd) {
        List<String> tokens = tokenize(command);
        if (tokens.isEmpty()) {
            return List.of();
        }

        String normalizedHead = normalize(tokens.get(0));
        if ("sudo".equals(normalizedHead) && tokens.size() > 1) {
            tokens = tokens.subList(1, tokens.size());
            normalizedHead = normalize(tokens.get(0));
        }

        if ("git".equals(normalizedHead) && tokens.size() >= 2 && "clone".equals(normalize(tokens.get(1)))) {
            if (tokens.size() >= 4) {
                return List.of(resolvePath(tokens.get(3), cwd));
            }
            return List.of(cwd);
        }

        LinkedHashSet<Path> targets = new LinkedHashSet<>();
        for (String item : tokens) {
            String token = stripQuotes(item);
            if (token.isBlank() || token.startsWith("-") || isShellOperator(token) || token.contains("=") || looksLikeUrl(token)) {
                continue;
            }
            if (isCommandKeyword(token) || "/dev/null".equals(token)) {
                continue;
            }
            targets.add(resolvePath(token, cwd));
        }
        return new ArrayList<>(targets);
    }

    private boolean hasWriteIntent(String command) {
        String normalized = " " + normalize(command) + " ";
        if (normalized.contains(" >") || normalized.contains(" >>") || normalized.contains(" rm ") || normalized.contains(" chmod ")
                || normalized.contains(" chown ") || normalized.contains(" mv ") || normalized.contains(" cp ")
                || normalized.contains(" sed -i") || normalized.contains(" unzip ") || normalized.contains(" tar ")) {
            return true;
        }
        return normalized.contains(" mkdir ") || normalized.contains(" touch ") || normalized.contains(" ln ");
    }

    private boolean matchesAnyPattern(String text, List<String> patterns) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String item : patterns) {
            try {
                if (Pattern.compile(item, Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                    return true;
                }
            } catch (Exception ignored) {
                // Ignore invalid regex from config.
            }
        }
        return false;
    }

    private boolean matchesPathRules(Path target, List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (String item : rules) {
            Path base = normalizeRulePath(item);
            if (base == null) {
                continue;
            }
            if (isUnder(base, target)) {
                return true;
            }
        }
        return false;
    }

    private Path normalizeRulePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(PathResolver.expandHome(raw.trim()));
            if (!path.isAbsolute()) {
                path = NomoClawPaths.root().resolve(path);
            }
            return normalizePath(path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path resolvePath(String raw, Path base) {
        try {
            return normalizePath(PathResolver.resolve(raw, base));
        } catch (Exception ignored) {
            return normalizePath(base);
        }
    }

    private boolean isProtectedSystemPath(Path target, Path workspace) {
        if (isUnder(workspace, target)) {
            return false;
        }
        String normalized = target.toString();
        for (String root : PROTECTED_ROOTS) {
            if (normalized.equals(root) || normalized.startsWith(root + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnder(Path parent, Path child) {
        Path normalizedParent = normalizePath(parent);
        Path normalizedChild = normalizePath(child);
        return normalizedChild.equals(normalizedParent) || normalizedChild.startsWith(normalizedParent);
    }

    private Path normalizePath(Path path) {
        if (path == null) {
            return Path.of(".").toAbsolutePath().normalize();
        }
        try {
            if (Files.exists(path)) {
                return path.toRealPath().normalize();
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return path.toAbsolutePath().normalize();
    }

    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher matcher = Pattern.compile("\"([^\"]*)\"|'([^']*)'|\\S+").matcher(command);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private boolean looksLikeUrl(String token) {
        String value = normalize(token);
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("git@");
    }

    private boolean isShellOperator(String token) {
        return "|".equals(token) || "&&".equals(token) || "||".equals(token) || ";".equals(token);
    }

    private boolean isCommandKeyword(String token) {
        String value = normalize(token);
        return Set.of("bash", "sh", "zsh", "python", "node", "ruby", "perl", "env", "command", "git", "clone").contains(value);
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

    private String summarizeCommand(String command) {
        String normalized = command == null ? "" : command.trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180) + "...";
    }

    private String summarizePaths(List<Path> paths) {
        return paths.stream().map(Path::toString).distinct().limit(3).reduce((left, right) -> left + ", " + right).orElse("");
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
