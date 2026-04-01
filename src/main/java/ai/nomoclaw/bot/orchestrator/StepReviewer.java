package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Set;

@Component
public class StepReviewer {

    private static final Set<String> TERMINAL_ERROR_CODES = Set.of(
            "INVALID_ARGS",
            "INVALID_ACTION",
            "EMPTY_ANSWER",
            "CANCELLED"
    );

    public ReviewDecision review(PlanStep step, ToolResult result) {
        if (!result.success()) {
            String message = result.errorMessage() == null ? "tool execution failed" : result.errorMessage();
            if (result.errorCode() != null
                    && (TERMINAL_ERROR_CODES.contains(result.errorCode()) || result.errorCode().startsWith("POLICY_"))) {
                return ReviewDecision.failure(message);
            }
            return ReviewDecision.retryableFailure(message);
        }

        JsonNode acceptance = step.toolArgs().path("acceptance");
        ReviewDecision structuredDecision = evaluateStructuredAcceptance(result, acceptance);
        if (structuredDecision != null) {
            return structuredDecision;
        }

        if (hasMeaningfulOutput(result) || allowsArtifactOnlySuccess(step, result)) {
            return ReviewDecision.pass();
        }

        String criteria = step.doneCriteria();
        if (criteria == null || criteria.isBlank()) {
            return ReviewDecision.pass();
        }
        return ReviewDecision.retryableFailure("done criteria not satisfied: empty output");
    }

    private ReviewDecision evaluateStructuredAcceptance(ToolResult result, JsonNode acceptance) {
        if (!acceptance.isObject()) {
            return null;
        }

        JsonNode outputContainsAll = acceptance.path("outputContainsAll");
        if (outputContainsAll.isArray()) {
            String output = safe(result.output());
            for (JsonNode item : outputContainsAll) {
                String expected = item.asText("");
                if (!expected.isBlank() && !output.contains(expected)) {
                    return ReviewDecision.failure("output does not contain expected text: " + expected);
                }
            }
        }

        JsonNode artifactsPresent = acceptance.path("artifactsPresent");
        if (artifactsPresent.isArray()) {
            JsonNode artifacts = result.artifacts();
            for (JsonNode item : artifactsPresent) {
                String field = item.asText("");
                if (!field.isBlank() && (artifacts == null || artifacts.path(field).isMissingNode() || artifacts.path(field).isNull())) {
                    return ReviewDecision.failure("missing artifact field: " + field);
                }
            }
        }

        if (!acceptance.path("allowEmptyOutput").asBoolean(false) && !hasMeaningfulOutput(result) && !hasUsefulArtifacts(result)) {
            return ReviewDecision.failure("acceptance requires output or artifacts");
        }
        return ReviewDecision.pass();
    }

    private boolean allowsArtifactOnlySuccess(PlanStep step, ToolResult result) {
        String toolName = safe(step.toolName());
        if ("cron_tool".equals(toolName)) {
            return hasUsefulArtifacts(result);
        }
        if ("answer_tool".equals(toolName)) {
            return hasMeaningfulOutput(result);
        }
        if ("file_tool".equals(toolName)) {
            String action = step.toolArgs().path("action").asText("");
            return ("write".equals(action) || "list".equals(action)) && hasUsefulArtifacts(result);
        }
        if ("browser_tool".equals(toolName)) {
            String action = step.toolArgs().path("action").asText("");
            return ("open".equals(action) || "click".equals(action) || "type".equals(action) || "screenshot".equals(action))
                    && hasUsefulArtifacts(result);
        }
        return false;
    }

    private boolean hasMeaningfulOutput(ToolResult result) {
        return result.output() != null && !result.output().isBlank();
    }

    private boolean hasUsefulArtifacts(ToolResult result) {
        JsonNode artifacts = result.artifacts();
        return artifacts != null && !artifacts.isMissingNode() && !artifacts.isNull() && artifacts.size() > 0;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    public record ReviewDecision(boolean passed, boolean retryable, String message) {
        public static ReviewDecision pass() {
            return new ReviewDecision(true, false, "ok");
        }

        public static ReviewDecision retryableFailure(String message) {
            return new ReviewDecision(false, true, message);
        }

        public static ReviewDecision failure(String message) {
            return new ReviewDecision(false, false, message);
        }
    }
}
