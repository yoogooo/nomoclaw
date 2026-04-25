package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.RiskLevel;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.orchestrator.StepReviewer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepReviewerTests {

    @Test
    void failedToolResultShouldBeRetryable() {
        StepReviewer reviewer = new StepReviewer();
        PlanStep step = new PlanStep(
                "s1",
                1,
                1,
                "run",
                "command_tool",
                JsonNodeFactory.instance.objectNode(),
                RiskLevel.LOW,
                "should output",
                StepStatus.CREATED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
        ToolResult result = ToolResult.failure("ERR", "failed", JsonNodeFactory.instance.objectNode());
        StepReviewer.ReviewDecision decision = reviewer.review(step, result);
        assertFalse(decision.passed());
        assertTrue(decision.retryable());
    }

    @Test
    void structuredAcceptanceMismatchShouldBeTerminalFailure() {
        StepReviewer reviewer = new StepReviewer();
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.putObject("acceptance").putArray("outputContainsAll").add("disk0");
        PlanStep step = new PlanStep(
                "s2",
                1,
                1,
                "inspect disk",
                "command_tool",
                args,
                RiskLevel.LOW,
                "output should mention disk0",
                StepStatus.CREATED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
        ToolResult result = ToolResult.success("volume1", JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode());
        StepReviewer.ReviewDecision decision = reviewer.review(step, result);
        assertFalse(decision.passed());
        assertFalse(decision.retryable());
    }
}
