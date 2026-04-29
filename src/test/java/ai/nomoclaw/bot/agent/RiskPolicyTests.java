package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.RiskLevel;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.policy.RiskPolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskPolicyTests {

    @Test
    void commandDeleteShouldRequireApproval() {
        AgentProperties properties = new AgentProperties();
        properties.getApproval().setHighRiskEnabled(true);
        RiskPolicy riskPolicy = new RiskPolicy(properties);

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("command", "rm -rf /tmp/test");
        PlanStep step = new PlanStep("s1", 1, 1, "danger", "CommandTool", args,
                RiskLevel.LOW, "done", StepStatus.CREATED, 0, null, null, ApprovalStatus.NONE);

        assertTrue(riskPolicy.requiresApproval(step));
    }

    @Test
    void readFileToolShouldBeLowRisk() {
        AgentProperties properties = new AgentProperties();
        RiskPolicy riskPolicy = new RiskPolicy(properties);
        assertEquals(RiskLevel.LOW, riskPolicy.evaluateRisk("ReadFileTool", JsonNodeFactory.instance.objectNode()));
    }

    @Test
    void createFileToolOutsideWorkspaceShouldBeHighRisk() {
        AgentProperties properties = new AgentProperties();
        RiskPolicy riskPolicy = new RiskPolicy(properties);

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("path", "/etc/hosts");
        assertEquals(RiskLevel.HIGH, riskPolicy.evaluateRisk("CreateFileTool", args));
    }
}
