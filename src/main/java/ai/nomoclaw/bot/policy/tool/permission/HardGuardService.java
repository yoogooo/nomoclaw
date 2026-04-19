package ai.nomoclaw.bot.policy.tool.permission;

import ai.nomoclaw.bot.policy.tool.ToolPolicyReasonCode;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;

@Component
public class HardGuardService {

    private static final Set<String> PROTECTED_NAMES = Set.of(".git", ".nomoclaw", ".vscode");
    private static final Set<String> UNIX_SYSTEM_ROOTS = Set.of(
            "/etc", "/usr", "/bin", "/sbin", "/var", "/System", "/Library", "/private", "/opt", "/boot", "/dev", "/proc"
    );
    private static final Set<String> WINDOWS_SYSTEM_ROOT_NAMES = Set.of(
            "windows",
            "program files",
            "program files (x86)",
            "programdata",
            "system volume information",
            "$recycle.bin"
    );

    public PermissionDecision evaluate(PermissionContextDetails details) {
        if (details == null || !details.writeIntent()) {
            return null;
        }

        List<Path> paths = details.resolvedPaths();
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        for (Path path : paths) {
            if (path == null) {
                continue;
            }
            for (Path candidate : candidates(path)) {
                if (isSystemPath(candidate)) {
                    return new PermissionDecision(
                            PermissionEffect.DENY,
                            ToolPolicyReasonCode.HARD_GUARD_SYSTEM_PATH_DENY,
                            "禁止修改系统关键目录。",
                            null,
                            "hardguard-system",
                            candidate.toString(),
                            true
                    );
                }
                if (containsProtectedName(candidate)) {
                    return new PermissionDecision(
                            PermissionEffect.ASK,
                            ToolPolicyReasonCode.HARD_GUARD_PROTECTED_PATH_ASK,
                            "命中受保护目录，需要手动确认。",
                            null,
                            "hardguard-protected",
                            candidate.toString(),
                            true
                    );
                }
            }
        }
        return null;
    }

    public List<String> protectedNames() {
        return PROTECTED_NAMES.stream().sorted().toList();
    }

    public List<String> systemRoots() {
        if (isWindows()) {
            return List.of(
                    "<Drive>:\\Windows",
                    "<Drive>:\\Program Files",
                    "<Drive>:\\Program Files (x86)",
                    "<Drive>:\\ProgramData",
                    "<Drive>:\\System Volume Information",
                    "<Drive>:\\$Recycle.Bin"
            );
        }
        return UNIX_SYSTEM_ROOTS.stream().sorted().toList();
    }

    private List<Path> candidates(Path path) {
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
            // ignore realpath failures
        }
        return out;
    }

    private boolean containsProtectedName(Path path) {
        boolean insensitive = isWindows() || isMac();
        for (Path part : path) {
            String value = part.toString();
            if (insensitive) {
                value = value.toLowerCase(Locale.ROOT);
            }
            for (String name : PROTECTED_NAMES) {
                String compared = insensitive ? name.toLowerCase(Locale.ROOT) : name;
                if (compared.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSystemPath(Path path) {
        if (isWindows()) {
            Path root = path.getRoot();
            if (root == null) {
                return false;
            }
            for (Path part : path) {
                String lowered = part.toString().toLowerCase(Locale.ROOT);
                if (WINDOWS_SYSTEM_ROOT_NAMES.contains(lowered)) {
                    return true;
                }
            }
            return false;
        }
        String value = path.toString();
        for (String root : UNIX_SYSTEM_ROOTS) {
            if (value.equals(root) || value.startsWith(root + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac");
    }
}
