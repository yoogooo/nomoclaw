package ai.nomoclaw.bot.policy.tool.permission;

import java.nio.file.Path;
import java.util.List;

public record PermissionContextDetails(
        String toolName,
        PermissionResourceType resourceType,
        String action,
        boolean readIntent,
        boolean writeIntent,
        String commandText,
        List<Path> resolvedPaths,
        Path resolvedCwd
) {
}
