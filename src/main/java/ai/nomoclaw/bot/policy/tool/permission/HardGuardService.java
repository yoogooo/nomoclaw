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
    private static final Set<String> SENSITIVE_DIR_NAMES = Set.of(
            ".ssh", ".gnupg", ".aws", ".kube"
    );
    private static final Set<String> SENSITIVE_FILE_NAMES = Set.of(
            ".env", ".env.local", ".env.production", ".env.development",
            "id_rsa", "id_ed25519", "id_dsa", "id_ecdsa",
            "authorized_keys", "credentials", "config.json"
    );
    private static final Set<String> SENSITIVE_FILE_SUFFIXES = Set.of(
            ".pem", ".key", ".p12", ".pfx", ".keystore"
    );
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
        if (details == null) {
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
                if (details.readIntent() && isSensitiveReadPath(candidate)) {
                    return new PermissionDecision(
                            PermissionEffect.ASK,
                            ToolPolicyReasonCode.HARD_GUARD_SENSITIVE_PATH_READ_ASK,
                            "检测到敏感信息路径读取，可能导致密钥或凭据泄露，请手动确认。",
                            null,
                            "hardguard-sensitive-read",
                            candidate.toString(),
                            true
                    );
                }
                if (!details.writeIntent()) {
                    continue;
                }
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
                    if (isWithinWorkspace(candidate, details.resolvedCwd())) {
                        continue;
                    }
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

    private boolean isWithinWorkspace(Path candidate, Path cwd) {
        if (candidate == null || cwd == null) {
            return false;
        }
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        Path normalizedWorkspace = cwd.toAbsolutePath().normalize();
        boolean insensitive = isWindows() || isMac();
        if (!insensitive) {
            return normalizedCandidate.startsWith(normalizedWorkspace);
        }
        String candidateText = normalizedCandidate.toString().toLowerCase(Locale.ROOT);
        String workspaceText = normalizedWorkspace.toString().toLowerCase(Locale.ROOT);
        return candidateText.equals(workspaceText)
                || candidateText.startsWith(workspaceText + "/")
                || candidateText.startsWith(workspaceText + "\\");
    }

    private boolean isSensitiveReadPath(Path path) {
        boolean insensitive = isWindows() || isMac();
        String pathText = normalizeForCompare(path.toString(), insensitive);
        String home = normalizeForCompare(System.getProperty("user.home", ""), insensitive);
        if (!home.isBlank()) {
            for (String dir : SENSITIVE_DIR_NAMES) {
                String root = home + (home.endsWith("/") || home.endsWith("\\") ? "" : "/") + normalizeForCompare(dir, insensitive);
                if (pathText.equals(root) || pathText.startsWith(root + "/") || pathText.startsWith(root + "\\")) {
                    return true;
                }
            }
        }

        Path fileNamePath = path.getFileName();
        String fileName = fileNamePath == null ? "" : normalizeForCompare(fileNamePath.toString(), insensitive);
        if (fileName.isBlank()) {
            return false;
        }
        if (SENSITIVE_FILE_NAMES.contains(fileName)) {
            if ("config.json".equals(fileName)) {
                return pathText.contains("/.docker/") || pathText.contains("\\.docker\\");
            }
            return true;
        }
        for (String suffix : SENSITIVE_FILE_SUFFIXES) {
            String normalizedSuffix = normalizeForCompare(suffix, insensitive);
            if (fileName.endsWith(normalizedSuffix)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForCompare(String value, boolean insensitive) {
        if (value == null) {
            return "";
        }
        String out = value.replace("\\", "/").trim();
        if (insensitive) {
            out = out.toLowerCase(Locale.ROOT);
        }
        return out;
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
