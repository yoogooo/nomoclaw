package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ConversationMessageRunDto;
import ai.nomoclaw.bot.application.dto.ConversationRunStepDto;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.AgentEvent;
import ai.nomoclaw.bot.model.AgentEventType;
import ai.nomoclaw.bot.model.PlanStep;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates persisted steps + event stream into the current run-view DTO.
 *
 * <p>Ordering and status normalization here must stay deterministic to avoid
 * UI jitter and historical run-view regressions.
 */
@Component
public class RunViewAssembler {

    private final ExecutionFeedbackBuilder feedbackBuilder;

    public RunViewAssembler(ExecutionFeedbackBuilder feedbackBuilder) {
        this.feedbackBuilder = feedbackBuilder;
    }

    public ConversationMessageRunDto toMessageRunResponse(AgentMessage message,
                                                          List<PlanStep> steps,
                                                          List<AgentEvent> events) {
        if ((steps == null || steps.isEmpty()) && (events == null || events.isEmpty())) {
            return null;
        }

        List<PlanStep> orderedSteps = steps == null ? List.of() : steps.stream()
                .sorted(Comparator.comparingInt(PlanStep::roundIndex).thenComparingInt(PlanStep::stepIndex))
                .toList();
        List<AgentEvent> safeEvents = events == null ? List.of() : events;

        Map<String, RunStepAccumulator> stepMap = new LinkedHashMap<>();
        for (PlanStep step : orderedSteps) {
            stepMap.put(step.stepUid(), RunStepAccumulator.fromStep(
                    step,
                    feedbackBuilder.buildDisplayTitle(step),
                    feedbackBuilder.summaryPlanned(),
                    feedbackBuilder.buildStepPlanDetails(step)
            ));
        }

        String runStatus = "planned";
        Instant updatedTime = message.createdAt();
        for (AgentEvent event : safeEvents) {
            updatedTime = event.timestamp();
            if (event.eventType() == AgentEventType.PLAN_CREATED) {
                JsonNode displaySteps = event.payload() == null ? null : event.payload().path("displaySteps");
                if (displaySteps != null && displaySteps.isArray()) {
                    for (JsonNode node : displaySteps) {
                        String stepUid = node.path("stepUid").asString("");
                        if (stepUid.isBlank()) {
                            continue;
                        }
                        stepMap.computeIfAbsent(stepUid, ignored -> RunStepAccumulator.fromEventNode(node))
                                .applyEventNode(node, event.timestamp());
                    }
                }
                runStatus = "planned";
                continue;
            }
            if (event.eventType() == AgentEventType.MESSAGE_COMPLETED) {
                runStatus = "COMPLETED".equalsIgnoreCase(event.payload().path("status").asString("")) ? "completed" : "failed";
                continue;
            }
            if (event.eventType() == AgentEventType.MESSAGE_CANCELED) {
                runStatus = "canceled";
                continue;
            }
            if (event.eventType() == AgentEventType.LOOP_LIMIT_REACHED) {
                runStatus = "failed";
                continue;
            }
            if (event.stepUid() == null || event.stepUid().isBlank()) {
                continue;
            }
            // Event-first upsert keeps rendering robust when a step node appears only in event stream.
            RunStepAccumulator accumulator = stepMap.computeIfAbsent(event.stepUid(), ignored -> RunStepAccumulator.empty(event.stepUid()));
            accumulator.applyEvent(event);
            if (event.eventType() == AgentEventType.STEP_WAITING_APPROVAL) {
                runStatus = "waiting_approval";
            } else if (event.eventType() == AgentEventType.STEP_STARTED) {
                runStatus = "running";
            }
        }

        Map<String, PlanStep> stepByUid = orderedSteps.stream()
                .collect(Collectors.toMap(PlanStep::stepUid, value -> value, (left, right) -> left, LinkedHashMap::new));
        for (Map.Entry<String, PlanStep> entry : stepByUid.entrySet()) {
            PlanStep step = entry.getValue();
            stepMap.computeIfAbsent(step.stepUid(), ignored -> RunStepAccumulator.fromStep(
                    step,
                    feedbackBuilder.buildDisplayTitle(step),
                    feedbackBuilder.summaryPlanned(),
                    feedbackBuilder.buildStepPlanDetails(step)
            ));
        }

        List<ConversationRunStepDto> stepResponses = stepMap.values().stream()
                .sorted(Comparator.comparingInt(RunStepAccumulator::roundIndex).thenComparingInt(RunStepAccumulator::stepIndex))
                .map(acc -> acc.toResponse(feedbackBuilder.defaultDisplayTitle(), feedbackBuilder.summaryPlanned()))
                .toList();
        if (stepResponses.isEmpty()) {
            return null;
        }
        int completedSteps = (int) stepResponses.stream().filter(step -> "completed".equals(step.status())).count();
        String normalizedStatus = normalizeRunStatus(runStatus, stepResponses);
        return new ConversationMessageRunDto(
                message.messageUid(),
                normalizedStatus,
                feedbackBuilder.buildRunSummary(normalizedStatus, completedSteps, stepResponses.size()),
                completedSteps,
                stepResponses.size(),
                updatedTime,
                stepResponses
        );
    }

    private String normalizeRunStatus(String currentStatus, List<ConversationRunStepDto> steps) {
        // Final terminal states always win; otherwise infer from the latest step-level states.
        if ("completed".equals(currentStatus) || "failed".equals(currentStatus) || "canceled".equals(currentStatus)) {
            return currentStatus;
        }
        if (steps.stream().anyMatch(step -> "failed".equals(step.status()) || "rejected".equals(step.status()))) {
            return "failed";
        }
        if (steps.stream().anyMatch(step -> "waiting_approval".equals(step.status()))) {
            return "waiting_approval";
        }
        if (steps.stream().anyMatch(step -> "running".equals(step.status()))) {
            return "running";
        }
        if (steps.stream().allMatch(step -> "completed".equals(step.status()))) {
            return "completed";
        }
        return currentStatus == null || currentStatus.isBlank() ? "planned" : currentStatus;
    }

    private static final class RunStepAccumulator {
        private final String stepUid;
        private int roundIndex;
        private int stepIndex;
        private String status = "planned";
        private String toolName = "";
        private JsonNode toolArgs = JsonNodeFactory.instance.objectNode();
        private String displayTitle = "";
        private String displaySummary = "";
        private String displayDetails = "";
        private String policyReasonCode = "";
        private Instant updatedTime = Instant.now();

        private RunStepAccumulator(String stepUid) {
            this.stepUid = stepUid;
        }

        static RunStepAccumulator fromStep(PlanStep step, String displayTitle, String displaySummary, String displayDetails) {
            RunStepAccumulator accumulator = new RunStepAccumulator(step.stepUid());
            accumulator.roundIndex = step.roundIndex();
            accumulator.stepIndex = step.stepIndex();
            accumulator.toolName = step.toolName() == null ? "" : step.toolName();
            accumulator.toolArgs = copyJson(step.toolArgs());
            accumulator.displayTitle = displayTitle;
            accumulator.displaySummary = displaySummary;
            accumulator.displayDetails = displayDetails;
            return accumulator;
        }

        static RunStepAccumulator fromEventNode(JsonNode node) {
            RunStepAccumulator accumulator = new RunStepAccumulator(node.path("stepUid").asString(""));
            accumulator.roundIndex = node.path("roundIndex").asInt(1);
            accumulator.stepIndex = node.path("stepIndex").asInt(1);
            accumulator.status = node.path("status").asString("planned");
            accumulator.toolName = node.path("toolName").asString("");
            accumulator.toolArgs = copyJson(node.path("toolArgs"));
            accumulator.displayTitle = node.path("displayTitle").asString("");
            accumulator.displaySummary = node.path("displaySummary").asString("");
            accumulator.displayDetails = node.path("displayDetails").asString("");
            accumulator.policyReasonCode = node.path("policyReasonCode").asString("");
            String updated = node.path("updatedTime").asString("");
            if (!updated.isBlank()) {
                accumulator.updatedTime = Instant.parse(updated);
            }
            return accumulator;
        }

        static RunStepAccumulator empty(String stepUid) {
            return new RunStepAccumulator(stepUid);
        }

        void applyEvent(AgentEvent event) {
            applyEventNode(event.payload(), event.timestamp());
            if (event.payload() == null || event.payload().path("status").asString("").isBlank()) {
                // Fallback mapping for events that do not carry explicit status.
                status = switch (event.eventType()) {
                    case STEP_STARTED -> "running";
                    case STEP_WAITING_APPROVAL -> "waiting_approval";
                    case STEP_FINISHED -> "completed";
                    case STEP_FAILED -> "failed";
                    case STEP_REJECTED -> "rejected";
                    default -> status;
                };
            }
        }

        void applyEventNode(JsonNode node, Instant eventTime) {
            if (node == null) {
                return;
            }
            roundIndex = node.path("roundIndex").asInt(roundIndex == 0 ? 1 : roundIndex);
            stepIndex = node.path("stepIndex").asInt(stepIndex == 0 ? 1 : stepIndex);
            status = node.path("status").asString(status == null || status.isBlank() ? "planned" : status);
            if (!node.path("toolName").asString("").isBlank()) {
                toolName = node.path("toolName").asString("");
            }
            if (node.hasNonNull("toolArgs") && node.path("toolArgs").isObject()) {
                toolArgs = copyJson(node.path("toolArgs"));
            }
            if (!node.path("displayTitle").asString("").isBlank()) {
                displayTitle = node.path("displayTitle").asString("");
            }
            if (!node.path("displaySummary").asString("").isBlank()) {
                displaySummary = node.path("displaySummary").asString("");
            }
            if (!node.path("displayDetails").asString("").isBlank()) {
                displayDetails = node.path("displayDetails").asString("");
            }
            if (!node.path("policyReasonCode").asString("").isBlank()) {
                policyReasonCode = node.path("policyReasonCode").asString("");
            }
            updatedTime = eventTime;
        }

        int roundIndex() {
            return roundIndex;
        }

        int stepIndex() {
            return stepIndex;
        }

        ConversationRunStepDto toResponse(String defaultTitle, String plannedSummary) {
            return new ConversationRunStepDto(
                    stepUid,
                    roundIndex,
                    stepIndex,
                    status == null || status.isBlank() ? "planned" : status,
                    toolName == null ? "" : toolName,
                    copyJson(toolArgs),
                    displayTitle == null || displayTitle.isBlank() ? defaultTitle : displayTitle,
                    displaySummary == null || displaySummary.isBlank() ? plannedSummary : displaySummary,
                    displayDetails == null ? "" : displayDetails,
                    policyReasonCode == null ? "" : policyReasonCode,
                    updatedTime
            );
        }

        private static JsonNode copyJson(JsonNode node) {
            if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
                return JsonNodeFactory.instance.objectNode();
            }
            return node.deepCopy();
        }
    }
}
