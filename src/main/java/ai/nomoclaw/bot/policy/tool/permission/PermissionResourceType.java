package ai.nomoclaw.bot.policy.tool.permission;

public enum PermissionResourceType {
    FILE,
    COMMAND,
    BROWSER,
    CRON,
    ANY;

    public static PermissionResourceType fromTool(String toolName) {
        String normalized = toolName == null ? "" : toolName.trim().toLowerCase();
        if (normalized.contains("file")) {
            return FILE;
        }
        if (normalized.contains("command")) {
            return COMMAND;
        }
        if (normalized.contains("browser")) {
            return BROWSER;
        }
        if (normalized.contains("cron")) {
            return CRON;
        }
        return ANY;
    }

    public static PermissionResourceType from(String value) {
        if (value == null || value.isBlank() || "*".equals(value.trim())) {
            return ANY;
        }
        try {
            return PermissionResourceType.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return ANY;
        }
    }
}
