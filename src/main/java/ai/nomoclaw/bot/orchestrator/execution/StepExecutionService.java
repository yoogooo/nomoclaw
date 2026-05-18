package ai.nomoclaw.bot.orchestrator.execution;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.AgentEvent;
import ai.nomoclaw.bot.model.AgentEventType;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.model.ToolProgress;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecision;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecisionResult;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.tool.ToolExecutor;
import ai.nomoclaw.bot.orchestrator.AgentEventBus;
import ai.nomoclaw.bot.orchestrator.ImageLoaderContextService;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.orchestrator.StepReviewer;
import ai.nomoclaw.bot.orchestrator.ToolExecutionPolicyGateway;
import ai.nomoclaw.bot.orchestrator.view.ExecutionFeedbackBuilder;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Executes a single step with retry/policy/progress handling and emits step events.
 *
 * <p>Behavior here mirrors historical AgentApplicationService semantics for
 * retry, status transitions, and event timing.
 */
@Component
@Slf4j
public class StepExecutionService {

    private static final String APPROVAL_MODE_FULL_ACCESS = "full_access";

    private final AgentProperties properties;
    private final AgentStore store;
    private final StepReviewer stepReviewer;
    private final ToolExecutor toolExecutor;
    private final ImageLoaderContextService imageLoaderContextService;
    private final ToolExecutionPolicyGateway toolExecutionPolicyGateway;
    private final MessageCancellationRegistry cancellationRegistry;
    private final AgentEventBus eventBus;
    private final ExecutionFeedbackBuilder feedbackBuilder;

    public StepExecutionService(AgentProperties properties,
                                AgentStore store,
                                StepReviewer stepReviewer,
                                ToolExecutor toolExecutor,
                                ImageLoaderContextService imageLoaderContextService,
                                ToolExecutionPolicyGateway toolExecutionPolicyGateway,
                                MessageCancellationRegistry cancellationRegistry,
                                AgentEventBus eventBus,
                                ExecutionFeedbackBuilder feedbackBuilder) {
        this.properties = properties;
        this.store = store;
        this.stepReviewer = stepReviewer;
        this.toolExecutor = toolExecutor;
        this.imageLoaderContextService = imageLoaderContextService;
        this.toolExecutionPolicyGateway = toolExecutionPolicyGateway;
        this.cancellationRegistry = cancellationRegistry;
        this.eventBus = eventBus;
        this.feedbackBuilder = feedbackBuilder;
    }

    public StepExecutionOutcome executeStepWithRetry(AgentMessage message,
                                                     PlanStep step,
                                                     int roundIndex,
                                                     RuntimeContext context) {
        int maxAttempts = Math.max(1, properties.getRetry().getMaxAttempts());
        ToolResult lastResult = null;
        String lastDecisionMessage = "";
        for (int attempt = step.retryCount(); attempt < maxAttempts; attempt++) {
            if (cancellationRegistry.isCanceled(message.messageUid())) {
                return StepExecutionOutcome.cancelled();
            }

            int currentAttempt = attempt + 1;
            store.updateStepStatus(step.stepUid(), StepStatus.RUNNING, attempt, null, null);
            log.info("[Agent] step start messageUid={} round={}/{} stepUid={} title={} tool={} attempt={}/{}",
                    message.messageUid(),
                    roundIndex,
                    properties.getLoop().getMaxRounds(),
                    step.stepUid(),
                    step.title(),
                    step.toolName(),
                    currentAttempt,
                    maxAttempts);
            ObjectNode startedPayload = feedbackBuilder.stepPayload(step, "attempt " + currentAttempt);
            feedbackBuilder.applyUserFacingFields(startedPayload, step, "running", feedbackBuilder.summaryRunning(), feedbackBuilder.buildStepStartedDetails(step));
            publishEvent(AgentEventType.STEP_STARTED, message.conversationUid(), message.messageUid(), step.stepUid(), startedPayload);

            ToolResult result = safeExecuteTool(message, step, roundIndex, currentAttempt, context);
            String outputText = resolveStepOutputText(step, result);
            lastResult = result;
            StepReviewer.ReviewDecision decision = stepReviewer.review(step, result);
            lastDecisionMessage = decision.message();

            if (decision.passed()) {
                store.updateStepStatus(step.stepUid(), StepStatus.COMPLETED, attempt, null, outputText);
                log.info("[Agent] step success messageUid={} round={}/{} stepUid={} attempt={} output={}",
                        message.messageUid(),
                        roundIndex,
                        properties.getLoop().getMaxRounds(),
                        step.stepUid(),
                        currentAttempt,
                        summarize(result.output()));
                publishEvent(AgentEventType.STEP_FINISHED, message.conversationUid(), message.messageUid(), step.stepUid(),
                        feedbackBuilder.resultPayload(step, result, currentAttempt, properties.getLoop().getMaxRounds()));
                StepExecutionOutcome outcome = StepExecutionOutcome.success(result, buildToolResultMessage(step, result, true, ""));
                return integrateImageLoaderContext(message, step, outcome);
            }

            boolean hasNextAttempt = decision.retryable() && currentAttempt < maxAttempts;
            store.updateStepStatus(step.stepUid(), StepStatus.FAILED, currentAttempt, decision.message(), outputText);
            log.warn("[Agent] step failed messageUid={} round={}/{} stepUid={} attempt={} retryable={} err={}",
                    message.messageUid(),
                    roundIndex,
                    properties.getLoop().getMaxRounds(),
                    step.stepUid(),
                    currentAttempt,
                    hasNextAttempt,
                    decision.message());
            ObjectNode failedPayload = feedbackBuilder.resultPayload(step, result, currentAttempt, properties.getLoop().getMaxRounds());
            failedPayload.put("retryable", hasNextAttempt);
            publishEvent(AgentEventType.STEP_FAILED, message.conversationUid(), message.messageUid(), step.stepUid(), failedPayload);
            if (!hasNextAttempt) {
                StepExecutionOutcome outcome = StepExecutionOutcome.failure(result, buildToolResultMessage(step, result, false, decision.message()));
                return integrateImageLoaderContext(message, step, outcome);
            }
        }

        ToolResult exhausted = lastResult != null
                ? lastResult
                : ToolResult.failure("RETRY_EXHAUSTED", "step retry exhausted", JsonNodeFactory.instance.objectNode());
        StepExecutionOutcome outcome = StepExecutionOutcome.failure(exhausted, buildToolResultMessage(step, exhausted, false, lastDecisionMessage));
        return integrateImageLoaderContext(message, step, outcome);
    }

    private ToolResult safeExecuteTool(AgentMessage message,
                                       PlanStep step,
                                       int roundIndex,
                                       int currentAttempt,
                                       RuntimeContext context) {
        try {
            if (isCronTool(step.toolName()) && isCronChannel(context.conversation().channel())) {
                // Scheduled runs cannot manage schedules themselves; skip with deterministic success payload.
                ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
                artifacts.put("skipped", true);
                artifacts.put("reason", "cron management tools are disabled during scheduled executions");
                ObjectNode metrics = JsonNodeFactory.instance.objectNode();
                metrics.put("skipped", true);
                return ToolResult.success("定时触发执行时已禁止管理定时任务。", artifacts, metrics);
            }
            ToolPolicyDecisionResult policyDecision = toolExecutionPolicyGateway.evaluateStep(
                    step,
                    context.workspaceConfig().workspaceDir(),
                    context.executionAgent() == null ? "" : context.executionAgent().getAgentUid(),
                    context.executionAgent() == null ? NomoClawPaths.DEFAULT_AGENT_NAME : context.executionAgent().getAgentName(),
                    context.conversation().channel(),
                    message.conversationUid(),
                    message.messageUid()
            );
            policyDecision = applyApprovalModeOverride(policyDecision, context.approvalMode());
            if (policyDecision.denied()) {
                // Return a tool-level failure result so reviewer + event flow remains unchanged.
                ObjectNode metrics = JsonNodeFactory.instance.objectNode();
                metrics.put("policyDenied", true);
                metrics.put("reasonCode", policyDecision.reasonCode().name());
                metrics.put("pathSummary", nullToEmpty(policyDecision.pathSummary()));
                return ToolResult.failure(
                        policyDecision.reasonCode().name(),
                        policyDecision.message().isBlank() ? "当前操作被安全策略阻止。" : policyDecision.message(),
                        metrics
                );
            }
            if (policyDecision.asks() && step.approvalStatus() != ApprovalStatus.APPROVED) {
                // Keep approval-gate behavior explicit for stale/unapproved planned steps.
                ObjectNode metrics = JsonNodeFactory.instance.objectNode();
                metrics.put("policyRequireApproval", true);
                metrics.put("reasonCode", policyDecision.reasonCode().name());
                metrics.put("pathSummary", nullToEmpty(policyDecision.pathSummary()));
                return ToolResult.failure(
                        policyDecision.reasonCode().name(),
                        policyDecision.message().isBlank() ? "当前操作需要先确认后执行。" : policyDecision.message(),
                        metrics
                );
            }
            long timeoutMs = resolveTimeoutMs(step.toolName());
            return toolExecutor.execute(
                    message.conversationUid(),
                    message.messageUid(),
                    context.executionAgent() == null ? "" : context.executionAgent().getAgentUid(),
                    context.executionAgent() == null ? "" : context.executionAgent().getAgentName(),
                    context.workspaceConfig().workspaceDir(),
                    context.workspaceConfig().tmpDir(),
                    context.workspaceConfig().reportDir(),
                    step,
                    timeoutMs,
                    progress -> publishStepProgress(message, step, roundIndex, currentAttempt, progress)
            );
        } catch (Exception ex) {
            log.error("[ToolExecutor] tool threw exception conversationUid={} messageUid={} stepUid={} tool={}",
                    message.conversationUid(), message.messageUid(), step.stepUid(), step.toolName(), ex);
            ObjectNode metrics = JsonNodeFactory.instance.objectNode();
            metrics.put("exception", ex.getClass().getSimpleName());
            return ToolResult.failure("TOOL_EXECUTION_ERROR", nullToEmpty(ex.getMessage()), metrics);
        }
    }

    private void publishStepProgress(AgentMessage message,
                                     PlanStep step,
                                     int roundIndex,
                                     int currentAttempt,
                                     ToolProgress progress) {
        if (progress == null) {
            return;
        }
        ObjectNode payload = feedbackBuilder.stepPayload(step, "attempt " + currentAttempt + " progress");
        String summary = hasMeaningfulText(progress.summary()) ? progress.summary().trim() : feedbackBuilder.summaryRunning();
        String details = hasMeaningfulText(progress.details()) ? progress.details().trim() : summary;
        feedbackBuilder.applyUserFacingFields(payload, step, "running", summary, details);
        payload.put("roundIndex", roundIndex);
        payload.put("silentLog", true);
        if (progress.metrics() != null && !progress.metrics().isNull()) {
            payload.set("progressMetrics", progress.metrics());
        }
        // Preserve historical event type for progress updates to keep frontend event handling stable.
        publishEvent(AgentEventType.STEP_STARTED, message.conversationUid(), message.messageUid(), step.stepUid(), payload);
    }

    private String resolveStepOutputText(PlanStep step, ToolResult result) {
        if (!"CommandTool".equals(nullToEmpty(step.toolName()))) {
            return null;
        }
        JsonNode artifacts = result.artifacts();
        String stdout = artifacts == null ? "" : artifacts.path("stdout").asString("");
        String stderr = artifacts == null ? "" : artifacts.path("stderr").asString("");
        String resolved = result.success()
                ? firstNonBlank(stdout, result.output())
                : firstNonBlank(result.errorMessage(), stderr, result.output());
        return abbreviate(nullToEmpty(resolved).trim(), 32 * 1024);
    }

    private StepExecutionOutcome integrateImageLoaderContext(AgentMessage message, PlanStep step, StepExecutionOutcome outcome) {
        if (outcome == null || outcome.canceled()) {
            return outcome;
        }
        ImageLoaderContextService.IntegrationResult integrationResult = imageLoaderContextService.integrate(message, step, outcome.toolResult());
        if (integrationResult == null) {
            return outcome;
        }
        String mergedMemoryText = appendMemoryTextLine(outcome.memoryText(), integrationResult.memoryLine());
        return new StepExecutionOutcome(
                false,
                outcome.toolResult(),
                mergedMemoryText,
                integrationResult.injectedMessage()
        );
    }

    private String appendMemoryTextLine(String origin, String appendix) {
        String left = origin == null ? "" : origin.trim();
        String right = appendix == null ? "" : appendix.trim();
        if (right.isBlank()) {
            return left;
        }
        if (left.isBlank()) {
            return right;
        }
        return left + "\n" + right;
    }

    private String buildToolResultMessage(PlanStep step, ToolResult result, boolean success, String reviewMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("step=").append(step.title()).append('\n');
        builder.append("tool=").append(step.toolName()).append('\n');
        builder.append("success=").append(success).append('\n');
        if (result.output() != null && !result.output().isBlank()) {
            builder.append("output=").append(result.output()).append('\n');
        }
        if (result.artifacts() != null && !result.artifacts().isNull() && result.artifacts().size() > 0) {
            builder.append("artifacts=").append(JsonUtil.toJson(result.artifacts())).append('\n');
        }
        if (result.errorCode() != null && !result.errorCode().isBlank()) {
            builder.append("errorCode=").append(result.errorCode()).append('\n');
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            builder.append("errorMessage=").append(result.errorMessage()).append('\n');
        }
        if (reviewMessage != null && !reviewMessage.isBlank()) {
            builder.append("review=").append(reviewMessage).append('\n');
        }
        return builder.toString().trim();
    }

    private void publishEvent(AgentEventType type, String conversationUid, String messageUid, String stepUid, ObjectNode payload) {
        // Event persistence and publish stay coupled to preserve replayability and live SSE consistency.
        AgentEvent event = new AgentEvent(
                UUID.randomUUID().toString(),
                type,
                conversationUid,
                messageUid,
                stepUid,
                Instant.now(),
                payload
        );
        store.appendEvent(event);
        eventBus.publish(event);
    }

    private ToolPolicyDecisionResult applyApprovalModeOverride(ToolPolicyDecisionResult decision, String approvalMode) {
        if (decision == null) {
            return decision;
        }
        if (!APPROVAL_MODE_FULL_ACCESS.equals(approvalMode)) {
            return decision;
        }
        if (decision.hardGuardHit()) {
            return decision;
        }
        if (decision.decision() == ToolPolicyDecision.ASK || decision.decision() == ToolPolicyDecision.DENY) {
            return ToolPolicyDecisionResult.of(
                    ToolPolicyDecision.ALLOW,
                    decision.reasonCode(),
                    decision.message(),
                    decision.pathSummary(),
                    decision.matchedSource(),
                    decision.matchedRuleId(),
                    false
            );
        }
        return decision;
    }

    private long resolveTimeoutMs(String toolName) {
        String normalizedToolName = nullToEmpty(toolName);
        if ("BrowserTool".equals(normalizedToolName)) {
            return properties.getBrowser().getStepTimeoutSeconds() * 1000L;
        }
        return properties.getParam().getTimeoutSeconds() * 1000L;
    }

    private boolean isCronChannel(String channel) {
        return channel != null && "cron".equalsIgnoreCase(channel.trim());
    }

    private boolean isCronTool(String toolName) {
        return switch (nullToEmpty(toolName)) {
            case "CronCreateTool", "CronDeleteTool", "CronListTool" -> true;
            default -> false;
        };
    }

    private boolean hasMeaningfulText(String text) {
        return text != null && !text.isBlank();
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() <= 120) {
            return compact;
        }
        return compact.substring(0, 117) + "...";
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String abbreviate(String text, int maxLength) {
        String normalized = nullToEmpty(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public record RuntimeContext(AgentConversation conversation,
                                 AgentDefinitionEntity executionAgent,
                                 AgentWorkspaceConfig workspaceConfig,
                                 String approvalMode) {
    }

    public record StepExecutionOutcome(boolean canceled, ToolResult toolResult, String memoryText, ChatMessage injectedMemoryMessage) {

        public static StepExecutionOutcome cancelled() {
            return new StepExecutionOutcome(true, null, "", null);
        }

        public static StepExecutionOutcome success(ToolResult result, String memoryText) {
            return new StepExecutionOutcome(false, result, memoryText, null);
        }

        public static StepExecutionOutcome failure(ToolResult result, String memoryText) {
            return new StepExecutionOutcome(false, result, memoryText, null);
        }
    }
}
