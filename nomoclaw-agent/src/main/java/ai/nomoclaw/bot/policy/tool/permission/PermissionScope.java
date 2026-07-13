package ai.nomoclaw.bot.policy.tool.permission;

public enum PermissionScope {
    ONCE,
    SESSION,
    AGENT,
    USER;

    public static PermissionScope from(String value) {
        if (value == null || value.isBlank()) {
            return ONCE;
        }
        return switch (value.trim().toLowerCase()) {
            case "session" -> SESSION;
            case "agent" -> AGENT;
            case "user" -> USER;
            default -> ONCE;
        };
    }
}
