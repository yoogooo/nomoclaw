package ai.nomoclaw.bot.policy;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.RiskLevel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;

@Component
public class RiskPolicy {

    private final AgentProperties properties;

    public RiskPolicy(AgentProperties properties) {
        this.properties = properties;
    }

    public boolean requiresApproval(PlanStep step) {
        if (!properties.getApproval().isHighRiskEnabled()) {
            return false;
        }
        return evaluateRisk(step.toolName(), step.toolArgs()) == RiskLevel.HIGH;
    }

    public RiskLevel evaluateRisk(String toolName, JsonNode toolArgs) {
        String tool = toolName == null ? "" : toolName.toLowerCase();
        if (tool.startsWith("mcp_")) {
            return RiskLevel.HIGH;
        }
        if (tool.contains("command")) {
            String cmd = toolArgs == null ? "" : toolArgs.path("command").asText("").toLowerCase();
            return cmd.contains("rm ") || cmd.contains("sudo ") || cmd.contains("chmod -r") || cmd.contains("mv ")
                    ? RiskLevel.HIGH
                    : RiskLevel.LOW;
        }
        if ("createfiletool".equals(tool) || "editfiletool".equals(tool)) {
            String pathRaw = toolArgs == null ? "" : toolArgs.path("path").asText("");
            return isAgentWorkspacePath(pathRaw) ? RiskLevel.LOW : RiskLevel.HIGH;
        }
        if ("readfiletool".equals(tool) || "listfiletool".equals(tool)) {
            return RiskLevel.LOW;
        }
        if (tool.contains("browser")) {
            return RiskLevel.LOW;
        }
        return RiskLevel.LOW;
    }

    private boolean isAgentWorkspacePath(String pathRaw) {
        if (pathRaw == null || pathRaw.isBlank()) {
            return false;
        }
        String expanded = expandHome(pathRaw.trim());
        Path path = Path.of(expanded);
        if (!path.isAbsolute()) {
            Path normalized = path.normalize();
            return !normalized.startsWith("..");
        }
        Path absolute = path.toAbsolutePath().normalize();
        return absolute.startsWith(NomoClawPaths.agentsRoot());
    }

    private String expandHome(String rawPath) {
        String home = System.getProperty("user.home");
        if ("~".equals(rawPath)) {
            return home;
        }
        if (rawPath.startsWith("~/")) {
            return home + rawPath.substring(1);
        }
        return rawPath;
    }
}
