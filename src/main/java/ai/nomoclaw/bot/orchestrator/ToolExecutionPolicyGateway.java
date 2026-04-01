package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.policy.tool.ToolPolicyContext;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecisionResult;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@Slf4j
public class ToolExecutionPolicyGateway {

    private final ToolPermissionPolicyService policyService;

    public ToolExecutionPolicyGateway(ToolPermissionPolicyService policyService) {
        this.policyService = policyService;
    }

    public ToolPolicyDecisionResult evaluate(String toolName,
                                             tools.jackson.databind.JsonNode toolArgs,
                                             Path agentWorkspacePath,
                                             String channel,
                                             String conversationUid,
                                             String messageUid,
                                             String stepUid) {
        ToolPolicyDecisionResult decision = policyService.evaluate(new ToolPolicyContext(
                toolName,
                toolArgs,
                agentWorkspacePath,
                channel,
                conversationUid,
                messageUid,
                stepUid
        ));
        if (decision.decision() != ToolPolicyDecision.ALLOW) {
            log.warn("[ToolPolicy] decision={} reasonCode={} channel={} conversationUid={} messageUid={} stepUid={} tool={} path={}",
                    decision.decision(),
                    decision.reasonCode(),
                    channel,
                    conversationUid,
                    messageUid,
                    stepUid,
                    toolName,
                    decision.pathSummary());
        }
        return decision;
    }

    public ToolPolicyDecisionResult evaluateStep(PlanStep step,
                                                 Path agentWorkspacePath,
                                                 String channel,
                                                 String conversationUid,
                                                 String messageUid) {
        return evaluate(
                step.toolName(),
                step.toolArgs(),
                agentWorkspacePath,
                channel,
                conversationUid,
                messageUid,
                step.stepUid()
        );
    }
}
