package ai.nomoclaw.bot.policy.tool.permission;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.policy.tool.ToolPolicyContext;
import ai.nomoclaw.bot.tool.PathResolver;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class CommandRuleResolver {

    private static final Set<String> COMMAND_WRITE_HINTS = Set.of(" >", " >>", " rm ", " chmod ", " chown ", " mv ", " cp ", " sed -i", " mkdir ", " touch ", " ln ", " unzip ", " tar ");
    private static final Set<String> READONLY_LINUX_COMMANDS = Set.of(
            "ls", "cat", "head", "tail", "grep", "wc", "sort", "uniq", "cut", "awk", "find", "stat", "du"
    );
    private static final Set<String> READONLY_CMD_COMMANDS = Set.of(
            "dir", "type", "findstr"
    );
    private static final Set<String> READONLY_POWERSHELL_COMMANDS = Set.of(
            "get-childitem", "ls", "dir",
            "get-content", "gc", "cat", "type",
            "select-object",
            "select-string",
            "measure-object",
            "sort-object",
            "where-object",
            "get-item",
            "get-filehash"
    );
    private static final Set<String> NON_READONLY_LINUX_COMMANDS = Set.of(
            "rm", "mv", "cp", "chmod", "chown", "mkdir", "touch", "ln", "tee", "dd", "mkfs", "mount", "umount", "tar", "unzip", "sed"
    );
    private static final Set<String> NON_READONLY_CMD_COMMANDS = Set.of(
            "del", "erase", "move", "copy", "ren", "mkdir", "rmdir", "md", "rd"
    );
    private static final Set<String> NON_READONLY_POWERSHELL_COMMANDS = Set.of(
            "set-content", "add-content", "out-file", "remove-item", "move-item", "copy-item",
            "new-item", "rename-item", "set-item", "clear-content", "invoke-expression", "iex"
    );
    private static final Set<String> SHELL_WRAPPERS = Set.of("bash", "sh", "zsh", "cmd", "powershell", "pwsh");

    public enum ReadonlyCommandVerdict {
        READ_ONLY,
        NOT_READ_ONLY,
        UNKNOWN
    }

    private enum ShellKind {
        LINUX,
        CMD,
        POWERSHELL
    }

    public PermissionContextDetails resolve(ToolPolicyContext context) {
        String tool = normalizeTool(context.toolName());
        if ("commandtool".equals(tool)) {
            String command = context.toolArgs().path("command").asString("");
            Path cwd = resolveCommandCwd(context);
            List<Path> targets = extractCommandWriteTargets(command, cwd);
            boolean writeIntent = hasWriteIntent(command);
            return new PermissionContextDetails(
                    context.toolName(),
                    PermissionResourceType.COMMAND,
                    "execute",
                    !writeIntent,
                    writeIntent,
                    command,
                    targets,
                    cwd
            );
        }

        if ("filetool".equals(tool)) {
            String action = context.toolArgs().path("action").asString("").trim().toLowerCase(Locale.ROOT);
            String pathRaw = context.toolArgs().path("path").asString("");
            Path base = context.agentWorkspacePath() == null ? Path.of(".").toAbsolutePath().normalize() : context.agentWorkspacePath();
            Path path = PathResolver.resolve(pathRaw, base);
            boolean write = Set.of("write", "append", "edit").contains(action);
            boolean read = Set.of("read", "list").contains(action);
            return new PermissionContextDetails(
                    context.toolName(),
                    PermissionResourceType.FILE,
                    action.isBlank() ? "*" : action,
                    read,
                    write,
                    "",
                    List.of(path),
                    base
            );
        }

        return new PermissionContextDetails(
                context.toolName(),
                PermissionResourceType.fromTool(tool),
                context.toolArgs().path("action").asString("*"),
                false,
                false,
                "",
                resolveGenericPaths(context),
                context.agentWorkspacePath()
        );
    }

    private List<Path> resolveGenericPaths(ToolPolicyContext context) {
        if (context == null || context.toolArgs() == null) {
            return List.of();
        }
        String tool = normalizeTool(context.toolName());
        if ("browsertool".equals(tool)) {
            String action = context.toolArgs().path("action").asString("").trim().toLowerCase(Locale.ROOT);
            if ("open".equals(action) || "navigate".equals(action)) {
                String host = BrowserPermissionSupport.extractHost(context.toolArgs().path("url").asString(""));
                if (!host.isBlank()) {
                    return List.of(BrowserPermissionSupport.browserDomainPath(host));
                }
            }
        }
        Path base = context.agentWorkspacePath() == null ? Path.of(".").toAbsolutePath().normalize() : context.agentWorkspacePath();
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        String pathArg = context.toolArgs().path("path").asString("");
        if (pathArg != null && !pathArg.isBlank()) {
            out.add(PathResolver.resolve(pathArg, base));
        }
        String outputArg = context.toolArgs().path("output").asString("");
        if (outputArg != null && !outputArg.isBlank()) {
            out.add(PathResolver.resolve(outputArg, base));
        }
        return out.isEmpty() ? List.of() : new ArrayList<>(out);
    }

    public List<PermissionRule> buildCommandRules(ToolPolicyContext context, PermissionContextDetails details) {
        if (!"commandtool".equals(normalizeTool(context.toolName()))) {
            return List.of();
        }
        String command = details.commandText() == null ? "" : details.commandText().trim();
        if (command.isBlank()) {
            return List.of();
        }
        String normalized = normalize(command);
        if (containsThirdPartyBrowserCliFallback(normalized)) {
            return List.of(new PermissionRule(
                    "command-deny-third-party-browser-cli-" + UuidUtil.newUuid(),
                    PermissionSource.COMMAND,
                    PermissionEffect.DENY,
                    "CommandTool",
                    "execute",
                    PermissionResourceType.COMMAND,
                    "",
                    "",
                    null,
                    true
            ));
        }

        if (matchesHighRiskPattern(command)) {
            return List.of(new PermissionRule(
                    "command-risk-" + UuidUtil.newUuid(),
                    PermissionSource.COMMAND,
                    PermissionEffect.ASK,
                    "CommandTool",
                    "execute",
                    PermissionResourceType.COMMAND,
                    "",
                    "",
                    null,
                    true
            ));
        }
        return List.of();
    }

    private boolean containsThirdPartyBrowserCliFallback(String normalizedCommand) {
        if (normalizedCommand == null || normalizedCommand.isBlank()) {
            return false;
        }
        String value = " " + normalizedCommand + " ";
        return value.contains(" agent-browser ")
                || value.contains(" npm install -g agent-browser")
                || value.contains(" npx agent-browser ");
    }

    public ReadonlyCommandVerdict isReadonlyParam(ToolPolicyContext context, PermissionContextDetails details) {
        if (!"commandtool".equals(normalizeTool(context.toolName()))) {
            return ReadonlyCommandVerdict.UNKNOWN;
        }
        String command = details == null ? "" : details.commandText();
        if (command == null || command.isBlank()) {
            return ReadonlyCommandVerdict.UNKNOWN;
        }
        return classify(command, inferShell(command));
    }

    private boolean matchesHighRiskPattern(String command) {
        String value = " " + normalize(command) + " ";
        return value.contains(" sudo ") || value.contains(" rm -rf") || value.contains(" mkfs ") || value.contains(" dd ")
               || value.contains(" mount ") || value.contains(" umount ") || value.contains(" launchctl ") || value.contains(" systemctl ")
               || value.contains(" crontab ") || value.contains(" chmod -r");
    }

    private Path resolveCommandCwd(ToolPolicyContext context) {
        String raw = context.toolArgs().path("cwd").asString("");
        Path workspace = context.agentWorkspacePath() == null ? Path.of(".").toAbsolutePath().normalize() : context.agentWorkspacePath().toAbsolutePath().normalize();
        if (raw == null || raw.isBlank()) {
            return workspace;
        }
        return PathResolver.resolve(raw, workspace);
    }

    private List<Path> extractCommandWriteTargets(String command, Path cwd) {
        List<String> tokens = tokenize(command);
        if (tokens.isEmpty()) {
            return List.of();
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
            try {
                targets.add(PathResolver.resolve(token, cwd));
            } catch (InvalidPathException ignored) {
                // Ignore non-path script fragments (for example PowerShell pipelines).
            }
        }
        return new ArrayList<>(targets);
    }

    private boolean hasWriteIntent(String command) {
        String value = " " + normalize(command) + " ";
        for (String marker : COMMAND_WRITE_HINTS) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private ReadonlyCommandVerdict classify(String command, ShellKind shell) {
        String normalized = normalize(command);
        if (normalized.isBlank()) {
            return ReadonlyCommandVerdict.UNKNOWN;
        }
        if (containsHighRiskExecutionSignal(normalized)) {
            return ReadonlyCommandVerdict.NOT_READ_ONLY;
        }
        if (hasWriteRedirection(normalized)) {
            return ReadonlyCommandVerdict.NOT_READ_ONLY;
        }

        ShellParam unwrapped = unwrapShellParam(command, shell);
        String effective = unwrapped.command();
        ShellKind effectiveShell = unwrapped.shellKind();
        List<String> segments = splitSegments(effective);
        if (segments.isEmpty()) {
            return ReadonlyCommandVerdict.UNKNOWN;
        }
        boolean allReadonly = true;
        for (String segment : segments) {
            String seg = segment == null ? "" : segment.trim();
            if (seg.isBlank()) {
                continue;
            }
            String first = firstToken(seg);
            if (first.isBlank()) {
                continue;
            }
            String firstNormalized = normalize(first);
            if (isNonReadonly(firstNormalized, effectiveShell, seg)) {
                return ReadonlyCommandVerdict.NOT_READ_ONLY;
            }
            if (!isReadonly(firstNormalized, effectiveShell)) {
                allReadonly = false;
            }
        }
        return allReadonly ? ReadonlyCommandVerdict.READ_ONLY : ReadonlyCommandVerdict.UNKNOWN;
    }

    private boolean containsHighRiskExecutionSignal(String normalizedParam) {
        String value = " " + normalizedParam + " ";
        return value.contains(" sudo ")
               || value.contains(" invoke-expression ")
               || value.contains(" iex ")
               || value.contains(" start-process ") && value.contains(" -verb runas");
    }

    private boolean hasWriteRedirection(String normalizedParam) {
        if (!normalizedParam.contains(">")) {
            return false;
        }
        java.util.regex.Matcher matcher = Pattern.compile("(^|\\s)\\d*>>?\\s*([^\\s|;&]+)").matcher(normalizedParam);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String target = matcher.group(2);
            if (target == null) {
                return true;
            }
            String normalizedTarget = normalize(target);
            if (!"/dev/null".equals(normalizedTarget) && !"nul".equals(normalizedTarget)) {
                return true;
            }
        }
        return !found;
    }

    private ShellKind inferShell(String command) {
        List<String> tokens = tokenize(command);
        if (!tokens.isEmpty()) {
            String first = normalize(stripQuotes(tokens.get(0)));
            if ("powershell".equals(first) || "pwsh".equals(first)) {
                return ShellKind.POWERSHELL;
            }
            if ("cmd".equals(first)) {
                return ShellKind.CMD;
            }
            if ("bash".equals(first) || "sh".equals(first) || "zsh".equals(first)) {
                return ShellKind.LINUX;
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? ShellKind.CMD : ShellKind.LINUX;
    }

    private ShellParam unwrapShellParam(String command, ShellKind shell) {
        List<String> tokens = tokenize(command);
        if (tokens.isEmpty()) {
            return new ShellParam(command, shell);
        }
        String first = normalize(stripQuotes(tokens.get(0)));
        if (!SHELL_WRAPPERS.contains(first)) {
            return new ShellParam(command, shell);
        }
        if (("powershell".equals(first) || "pwsh".equals(first)) && tokens.size() >= 3) {
            for (int i = 1; i < tokens.size() - 1; i++) {
                String option = normalize(stripQuotes(tokens.get(i)));
                if ("-command".equals(option) || "-c".equals(option)) {
                    return new ShellParam(stripQuotes(tokens.get(i + 1)), ShellKind.POWERSHELL);
                }
            }
        }
        if ("cmd".equals(first) && tokens.size() >= 3) {
            for (int i = 1; i < tokens.size() - 1; i++) {
                String option = normalize(stripQuotes(tokens.get(i)));
                if ("/c".equals(option) || "/k".equals(option)) {
                    return new ShellParam(stripQuotes(tokens.get(i + 1)), ShellKind.CMD);
                }
            }
        }
        if (("bash".equals(first) || "sh".equals(first) || "zsh".equals(first)) && tokens.size() >= 3) {
            for (int i = 1; i < tokens.size() - 1; i++) {
                String option = normalize(stripQuotes(tokens.get(i)));
                if ("-c".equals(option)) {
                    return new ShellParam(stripQuotes(tokens.get(i + 1)), ShellKind.LINUX);
                }
            }
        }
        return new ShellParam(command, shell);
    }

    private List<String> splitSegments(String command) {
        List<String> out = new ArrayList<>();
        if (command == null || command.isBlank()) {
            return out;
        }
        String[] raw = command.split("\\|\\||&&|\\||;");
        for (String item : raw) {
            String segment = item == null ? "" : item.trim();
            if (!segment.isBlank()) {
                out.add(segment);
            }
        }
        return out;
    }

    private String firstToken(String segment) {
        List<String> tokens = tokenize(segment);
        if (tokens.isEmpty()) {
            return "";
        }
        return stripQuotes(tokens.get(0));
    }

    private boolean isNonReadonly(String first, ShellKind shell, String segment) {
        String loweredSegment = " " + normalize(segment) + " ";
        if (shell == ShellKind.POWERSHELL) {
            if (NON_READONLY_POWERSHELL_COMMANDS.contains(first)) {
                return true;
            }
            return "start-process".equals(first) && loweredSegment.contains(" -verb runas");
        }
        if (shell == ShellKind.CMD) {
            return NON_READONLY_CMD_COMMANDS.contains(first);
        }
        if (NON_READONLY_LINUX_COMMANDS.contains(first)) {
            if ("tar".equals(first)) {
                return loweredSegment.contains(" -x") || loweredSegment.contains(" --extract");
            }
            if ("sed".equals(first)) {
                return loweredSegment.contains(" -i");
            }
            return true;
        }
        return false;
    }

    private boolean isReadonly(String first, ShellKind shell) {
        if (shell == ShellKind.POWERSHELL) {
            return READONLY_POWERSHELL_COMMANDS.contains(first);
        }
        if (shell == ShellKind.CMD) {
            return READONLY_CMD_COMMANDS.contains(first);
        }
        return READONLY_LINUX_COMMANDS.contains(first);
    }

    private record ShellParam(String command, ShellKind shellKind) {
    }

    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher matcher = Pattern.compile("\"([^\"]*)\"|'([^']*)'|\\S+").matcher(command == null ? "" : command);
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
        return Set.of(
                "bash", "sh", "zsh", "python", "node", "ruby", "perl", "env", "command", "git", "clone",
                "mkdir", "cp", "mv", "rm", "chmod", "chown", "touch", "ln", "sed", "tee", "tar", "unzip",
                "rsync", "cat", "echo", "install", "find", "xargs"
        ).contains(value);
    }

    private String stripQuotes(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String out = token.trim();
        if ("\"".equals(out) || "'".equals(out)) {
            return "";
        }
        if (out.length() >= 2
            && ((out.startsWith("\"") && out.endsWith("\"")) || (out.startsWith("'") && out.endsWith("'")))) {
            out = out.substring(1, out.length() - 1);
        }
        return out.trim();
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeTool(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
