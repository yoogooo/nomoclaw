package ai.nomoclaw.bot.policy.tool.permission;

import ai.nomoclaw.bot.policy.tool.ToolPolicyContext;
import ai.nomoclaw.bot.tool.PathResolver;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CommandRuleResolver {

    private static final Set<String> COMMAND_WRITE_HINTS = Set.of(" >", " >>", " rm ", " chmod ", " chown ", " mv ", " cp ", " sed -i", " mkdir ", " touch ", " ln ", " unzip ", " tar ");

    public PermissionContextDetails resolve(ToolPolicyContext context) {
        String tool = normalize(context.toolName());
        if ("command_tool".equals(tool)) {
            String command = context.toolArgs().path("command").asText("");
            Path cwd = resolveCommandCwd(context);
            List<Path> targets = extractCommandWriteTargets(command, cwd);
            boolean writeIntent = hasWriteIntent(command);
            return new PermissionContextDetails(
                    PermissionResourceType.COMMAND,
                    "execute",
                    !writeIntent,
                    writeIntent,
                    command,
                    targets,
                    cwd
            );
        }

        if ("file_tool".equals(tool) || "file_io_tool".equals(tool)) {
            String action = context.toolArgs().path("action").asText("").trim().toLowerCase(Locale.ROOT);
            String pathRaw = context.toolArgs().path("path").asText("");
            Path base = context.agentWorkspacePath() == null ? Path.of(".").toAbsolutePath().normalize() : context.agentWorkspacePath();
            Path path = PathResolver.resolve(pathRaw, base);
            boolean write = Set.of("write", "append", "edit").contains(action);
            boolean read = Set.of("read", "list").contains(action);
            return new PermissionContextDetails(
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
                PermissionResourceType.fromTool(tool),
                context.toolArgs().path("action").asText("*"),
                false,
                false,
                "",
                List.of(),
                context.agentWorkspacePath()
        );
    }

    public List<PermissionRule> buildCommandRules(ToolPolicyContext context, PermissionContextDetails details) {
        if (!"command_tool".equals(normalize(context.toolName()))) {
            return List.of();
        }
        String command = details.commandText() == null ? "" : details.commandText().trim();
        if (command.isBlank()) {
            return List.of();
        }

        if (matchesHighRiskPattern(command)) {
            return List.of(new PermissionRule(
                    "command-risk-" + UUID.randomUUID(),
                    PermissionSource.COMMAND,
                    PermissionEffect.ASK,
                    "command_tool",
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

    private boolean matchesHighRiskPattern(String command) {
        String value = " " + normalize(command) + " ";
        return value.contains(" sudo ") || value.contains(" rm -rf") || value.contains(" mkfs ") || value.contains(" dd ")
                || value.contains(" mount ") || value.contains(" umount ") || value.contains(" launchctl ") || value.contains(" systemctl ")
                || value.contains(" crontab ") || value.contains(" chmod -r");
    }

    private Path resolveCommandCwd(ToolPolicyContext context) {
        String raw = context.toolArgs().path("cwd").asText("");
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
            targets.add(PathResolver.resolve(token, cwd));
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
        if ((out.startsWith("\"") && out.endsWith("\"")) || (out.startsWith("'") && out.endsWith("'"))) {
            out = out.substring(1, out.length() - 1);
        }
        return out.trim();
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
