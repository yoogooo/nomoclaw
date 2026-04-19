package ai.nomoclaw.bot.policy.tool.permission;

import ai.nomoclaw.bot.policy.tool.ToolPolicyReasonCode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Component
public class HardGuardService {

    private static final Set<String> PROTECTED_NAMES = Set.of(".git", ".nomoclaw", ".vscode");
    private static final Set<String> SYSTEM_ROOTS = Set.of(
            "/etc", "/usr", "/bin", "/sbin", "/var", "/System", "/Library", "/private", "/opt", "/boot", "/dev", "/proc"
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
            Path normalized = path.toAbsolutePath().normalize();
            String value = normalized.toString();
            for (String root : SYSTEM_ROOTS) {
                if (value.equals(root) || value.startsWith(root + "/")) {
                    return new PermissionDecision(
                            PermissionEffect.DENY,
                            ToolPolicyReasonCode.HARD_GUARD_SYSTEM_PATH_DENY,
                            "禁止修改系统关键目录。",
                            null,
                            "hardguard-system",
                            normalized.toString(),
                            true
                    );
                }
            }
            for (Path part : normalized) {
                if (PROTECTED_NAMES.contains(part.toString())) {
                    return new PermissionDecision(
                            PermissionEffect.ASK,
                            ToolPolicyReasonCode.HARD_GUARD_PROTECTED_PATH_ASK,
                            "命中受保护目录，需要手动确认。",
                            null,
                            "hardguard-protected",
                            normalized.toString(),
                            true
                    );
                }
            }
        }
        return null;
    }
}
