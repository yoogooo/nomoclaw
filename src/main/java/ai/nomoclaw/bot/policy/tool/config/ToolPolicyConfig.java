package ai.nomoclaw.bot.policy.tool.config;

import java.util.List;

public record ToolPolicyConfig(
        List<String> allowWritePaths,
        List<String> denyWritePaths,
        List<String> highRiskCommandPatterns,
        List<String> highRiskFileActions
) {
    public static ToolPolicyConfig defaults() {
        return new ToolPolicyConfig(
                List.of(),
                List.of(),
                List.of("(^|\\s)(sudo\\s+)?(rm|mv|chmod|chown|dd|mkfs|mount|umount|launchctl|systemctl|crontab|at|atrm|atq)(\\s|$)",
                        "(^|\\s)(tar\\s+.*-x|unzip\\s|sed\\s+-i|tee\\s)",
                        "(>|>>)") ,
                List.of()
        );
    }
}
