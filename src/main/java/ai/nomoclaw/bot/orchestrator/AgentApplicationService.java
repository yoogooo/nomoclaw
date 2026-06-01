package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.channel.model.ChannelMessageCompletedEvent;
import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.conversation.model.ApprovalDecisionDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageRunDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.llm.config.LlmProperties;
import ai.nomoclaw.bot.model.*;
import ai.nomoclaw.bot.orchestrator.approval.ApprovalGrantedEvent;
import ai.nomoclaw.bot.orchestrator.execution.*;
import ai.nomoclaw.bot.conversation.service.ConversationService;
import ai.nomoclaw.bot.orchestrator.view.ExecutionFeedbackBuilder;
import ai.nomoclaw.bot.planner.Planner;
import ai.nomoclaw.bot.policy.RiskPolicy;
import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecision;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecisionResult;
import ai.nomoclaw.bot.policy.tool.ToolPolicyReasonCode;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEffect;
import ai.nomoclaw.bot.policy.tool.permission.PermissionScope;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSource;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.conversation.support.ConversationAttachmentService;
import ai.nomoclaw.bot.system.model.SystemConfigDto;
import ai.nomoclaw.bot.tool.PlatformSupport;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.awt.*;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.abbreviate;

/**
 * Agent 领域应用层 Facade。
 *
 * <p>主要职责：
 * 1) 对外提供会话/消息/Agent 管理入口
 * 2) 处理参数归一化、权限决策、持久化协调
 * 3) 将执行链路路由到独立编排与执行组件
 *
 * <p>当前协作边界：
 * - 消息主循环编排由 {@link MessageExecutionOrchestrator} 负责
 * - 单步骤执行与重试由 {@link StepExecutionService} 负责
 * - 运行反馈与展示字段组装由 {@link ExecutionFeedbackBuilder} / {@link ai.nomoclaw.bot.orchestrator.view.RunViewAssembler} 负责
 */
@Service
@Slf4j
public class AgentApplicationService {

    private static final String STOP_REASON_MAX_LOOP_REACHED = "MAX_LOOP_REACHED";
    private static final String APPROVAL_MODE_DEFAULT = "default";
    private static final String APPROVAL_MODE_FULL_ACCESS = "full_access";
    private static final int CONVERSATION_CONTEXT_LIMIT = 30;

    private final AgentStore store;
    private final Planner planner;
    private final RiskPolicy riskPolicy;
    private final AgentEventBus eventBus;
    private final MessageCancellationRegistry cancellationRegistry;
    private final AgentProperties properties;
    private final LlmProperties llmProperties;
    private final ConversationAttachmentService conversationAttachmentAppService;
    private final ToolExecutionPolicyGateway toolExecutionPolicyGateway;
    private final ToolPermissionPolicyService toolPermissionPolicyService;
    private final PermissionAppService permissionAppService;
    private final ExecutionFeedbackBuilder feedbackBuilder;
    private final StepExecutionService stepExecutionService;
    private final ExecutionScopeResolver executionScopeResolver;
    private final MessageExecutionOrchestrator messageExecutionOrchestrator;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ConversationService conversationService;

    public AgentApplicationService(AgentStore store,
                                   Planner planner,
                                   RiskPolicy riskPolicy,
                                   AgentEventBus eventBus,
                                   MessageCancellationRegistry cancellationRegistry,
                                   AgentProperties properties,
                                   LlmProperties llmProperties,
                                   ConversationAttachmentService conversationAttachmentAppService,
                                   ToolExecutionPolicyGateway toolExecutionPolicyGateway,
                                   ToolPermissionPolicyService toolPermissionPolicyService,
                                   PermissionAppService permissionAppService,
                                   ExecutionFeedbackBuilder feedbackBuilder,
                                   StepExecutionService stepExecutionService,
                                   ExecutionScopeResolver executionScopeResolver,
                                   MessageExecutionOrchestrator messageExecutionOrchestrator,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   ConversationService conversationService) {
        this.store = store;
        this.planner = planner;
        this.riskPolicy = riskPolicy;
        this.eventBus = eventBus;
        this.cancellationRegistry = cancellationRegistry;
        this.properties = properties;
        this.llmProperties = llmProperties;
        this.conversationAttachmentAppService = conversationAttachmentAppService;
        this.toolExecutionPolicyGateway = toolExecutionPolicyGateway;
        this.toolPermissionPolicyService = toolPermissionPolicyService;
        this.permissionAppService = permissionAppService;
        this.feedbackBuilder = feedbackBuilder;
        this.stepExecutionService = stepExecutionService;
        this.executionScopeResolver = executionScopeResolver;
        this.messageExecutionOrchestrator = messageExecutionOrchestrator;
        this.applicationEventPublisher = applicationEventPublisher;
        this.conversationService = conversationService;
    }

    public String createConversation(String agentGroupUid, String agentUid) {
        return createConversation(agentGroupUid, agentUid, "web");
    }

    public String createConversation(String agentGroupUid, String agentUid, String channel) {
        return conversationService.createConversation(agentGroupUid, agentUid, channel);
    }

    public List<ConversationSummaryDto> listConversations() {
        return conversationService.listConversations();
    }


    public List<ConversationMessageDto> listMessages(String conversationUid) {
        return conversationService.listMessages(conversationUid);
    }

    public List<ConversationMessageRunDto> listMessageRuns(String conversationUid) {
        return conversationService.listMessageRuns(conversationUid);
    }

    public void deleteConversation(String conversationUid) {
        conversationService.deleteConversation(conversationUid);
    }

    public void updateConversationTitle(String conversationUid, String title) {
        conversationService.updateConversationTitle(conversationUid, title);
    }

    public void updateConversationPinned(String conversationUid, boolean pinned) {
        conversationService.updateConversationPinned(conversationUid, pinned);
    }

    public void markConversationRead(String conversationUid) {
        conversationService.markConversationRead(conversationUid);
    }

    public String submitMessage(String conversationUid, String message) {
        return submitMessage(conversationUid, message, "web");
    }

    public String submitMessage(String conversationUid, String message, String channel) {
        return submitMessage(conversationUid, message, List.of(), "", "", APPROVAL_MODE_DEFAULT, channel, null);
    }

    public String submitMessage(String conversationUid, String message, String channel, Consumer<String> beforeExecuteHook) {
        return submitMessage(conversationUid, message, List.of(), "", "", APPROVAL_MODE_DEFAULT, channel, beforeExecuteHook);
    }

    /**
     * 提交用户消息并异步触发执行。
     *
     * <p>行为要点：
     * - message 级模型选择：本次请求的 modelProvider/modelName 落在 message 上
     * - 附件先绑定 message，再由执行链路消费
     * - 仅负责入库和排队，不在当前线程执行模型推理
     */
    public String submitMessage(String conversationUid,
                                String message,
                                List<String> fileUrls,
                                String modelProvider,
                                String modelName,
                                String approvalMode,
                                String channel,
                                Consumer<String> beforeExecuteHook) {
        return conversationService.submitMessage(
                conversationUid,
                message,
                fileUrls,
                modelProvider,
                modelName,
                approvalMode,
                channel,
                beforeExecuteHook,
                executionDriver()
        );
    }

    public AgentMessage getMessage(String messageUid) {
        return conversationService.getMessage(messageUid);
    }

    public String updateConversationApprovalMode(String conversationUid, String approvalMode, boolean applyToRunning) {
        return conversationService.updateApprovalMode(conversationUid, approvalMode, applyToRunning);
    }

    public int maxLoopRounds() {
        return conversationService.maxLoopRounds();
    }

    public SystemConfigDto getSystemConfig() {
        return new SystemConfigDto(
                NomoClawPaths.root().toString(),
                NomoClawPaths.agentsRoot().toString(),
                NomoClawPaths.skillsRoot().toString()
        );
    }

    public void openFile(String path) {
        try {
            Path file = Path.of(path).toAbsolutePath().normalize();
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("file not found: " + file);
            }
            if (!Files.isRegularFile(file) && !Files.isDirectory(file)) {
                throw new IllegalArgumentException("path is not an openable file or directory: " + file);
            }
            openPathInHostOs(file);
            log.info("[Agent] open file path={}", file);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to open file: " + path, ex);
        }
    }

    public ApprovalDecisionDto decideStep(String conversationUid,
                                          String stepUid,
                                          String action,
                                          PermissionScope scope,
                                          String note) {
        PlanStep step = store.findStep(stepUid)
                .orElseThrow(() -> new IllegalArgumentException("step not found: " + stepUid));
        String messageUid = store.findMessageIdByStep(stepUid)
                .orElseThrow(() -> new IllegalArgumentException("message not found for step: " + stepUid));
        AgentMessage message = store.findMessage(messageUid)
                .orElseThrow(() -> new IllegalArgumentException("message not found: " + messageUid));
        AgentConversation conversation = requireConversation(message.conversationUid());
        ExecutionApprovalScope approvalScope = executionScopeResolver.resolveForApproval(conversation, message);
        AgentDefinitionEntity executionAgent = approvalScope.executionAgent();
        String agentName = executionAgent == null ? NomoClawPaths.DEFAULT_AGENT_NAME : executionAgent.getAgentName();
        AgentWorkspaceConfig workspaceConfig = approvalScope.workspaceConfig();
        String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        PermissionScope appliedScope = scope == null ? PermissionScope.ONCE : scope;
        String matchedRuleId = "";
        ToolPolicyReasonCode policyReasonCode = resolveStepPolicyReasonCode(approvalScope, step);
        if (isHardGuardAskReason(policyReasonCode)
            && (appliedScope == PermissionScope.AGENT || appliedScope == PermissionScope.USER)) {
            appliedScope = PermissionScope.SESSION;
        }

        if ("allow".equals(normalizedAction)) {
            if (appliedScope != PermissionScope.ONCE) {
                var rule = permissionAppService.ruleFromApproval(
                        step.toolName(),
                        step.toolArgs(),
                        PermissionEffect.ALLOW,
                        appliedScope == PermissionScope.SESSION ? PermissionSource.SESSION
                                : (appliedScope == PermissionScope.AGENT ? PermissionSource.AGENT_SETTINGS : PermissionSource.USER_SETTINGS),
                        workspaceConfig.workspaceDir()
                );
                matchedRuleId = rule.ruleId();
                toolPermissionPolicyService.persistRule(appliedScope, message.conversationUid(), agentName, rule);
            }
            store.updateStepApproval(stepUid, ApprovalStatus.APPROVED, StepStatus.CREATED);
            store.updateStepStatus(stepUid, StepStatus.CREATED, 0, null, null);
            log.info("[Agent] step approved conversationUid={} stepUid={} round={} scope={} note={}",
                    conversationUid, stepUid, step.roundIndex(), appliedScope, nullToEmpty(note));
            if (message.status() != MessageStatus.COMPLETED && message.status() != MessageStatus.CANCELED) {
                messageExecutionOrchestrator.resume(message.messageUid(), executionDriver());
            }
            applicationEventPublisher.publishEvent(new ApprovalGrantedEvent(message.messageUid()));
            return new ApprovalDecisionDto("accepted", appliedScope.name().toLowerCase(Locale.ROOT), appliedScope != PermissionScope.ONCE, matchedRuleId);
        }

        store.updateStepApproval(stepUid, ApprovalStatus.REJECTED, StepStatus.FAILED);
        if (appliedScope != PermissionScope.ONCE) {
            var rule = permissionAppService.ruleFromApproval(
                    step.toolName(),
                    step.toolArgs(),
                    PermissionEffect.DENY,
                    appliedScope == PermissionScope.SESSION ? PermissionSource.SESSION
                            : (appliedScope == PermissionScope.AGENT ? PermissionSource.AGENT_SETTINGS : PermissionSource.USER_SETTINGS),
                    workspaceConfig.workspaceDir()
            );
            matchedRuleId = rule.ruleId();
            toolPermissionPolicyService.persistRule(appliedScope, message.conversationUid(), agentName, rule);
        }
        cancellationRegistry.cancel(messageUid);
        log.info("[Agent] step rejected conversationUid={} stepUid={} round={} scope={} note={}",
                conversationUid, stepUid, step.roundIndex(), appliedScope, nullToEmpty(note));
        ObjectNode rejectedPayload = stepPayload(step, "approval rejected by user");
        feedbackBuilder.applyUserFacingFields(rejectedPayload, step, "rejected",
                feedbackBuilder.summaryRejected(),
                feedbackBuilder.detailsRejected());
        publishEvent(AgentEventType.STEP_REJECTED, conversationUid, messageUid, stepUid, rejectedPayload);
        failMessage(message, feedbackBuilder.messageFailedApprovalRejected(), "APPROVAL_REJECTED");
        return new ApprovalDecisionDto("accepted", appliedScope.name().toLowerCase(Locale.ROOT), appliedScope != PermissionScope.ONCE, matchedRuleId);
    }

    public void cancelLatestMessage(String conversationUid) {
        AgentMessage message = store.findLatestUserMessageByConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("message not found"));
        store.updateMessageStatus(message.messageUid(), MessageStatus.CANCELED);
        cancellationRegistry.cancel(message.messageUid());
        log.info("[Agent] message canceled conversationUid={} messageUid={}", conversationUid, message.messageUid());
        ObjectNode payload = basePayload("message canceled");
        String partialAnswer = messageExecutionOrchestrator.cancel(message.messageUid()).trim();
        payload.put("answer", partialAnswer);
        publishEvent(AgentEventType.MESSAGE_CANCELED, conversationUid, message.messageUid(), null, payload);
        publishChannelMessageCompletedEvent(message, MessageStatus.CANCELED, feedbackBuilder.messageCanceled());
    }

    public SseEmitter subscribe(String conversationUid) {
        return eventBus.subscribe(conversationUid);
    }

    private MessageExecutionOrchestrator.ExecutionDriver executionDriver() {
        return new MessageExecutionOrchestrator.ExecutionDriver() {
            @Override
            public AgentMessage requireMessage(String messageUid) {
                return store.findMessage(messageUid)
                        .orElseThrow(() -> new IllegalArgumentException("message not found: " + messageUid));
            }

            @Override
            public List<ChatMessage> buildConversationMemory(AgentMessage initialMessage) {
                return AgentApplicationService.this.buildConversationMemory(initialMessage);
            }

            @Override
            public int maxRounds() {
                return properties.getLoop().getMaxRounds();
            }

            @Override
            public List<PlanStep> pendingStepsForRound(String messageUid, int roundIndex) {
                return AgentApplicationService.this.pendingStepsForRound(messageUid, roundIndex);
            }

            @Override
            public RoundPlanningResult reasonNextAction(AgentMessage message,
                                                        MessageExecutionRuntimeState state,
                                                        ExecutionRuntimeStateStore runtimeStateStore) {
                return AgentApplicationService.this.reasonNextAction(message, state, runtimeStateStore);
            }

            @Override
            public RoundExecutionResult executeRound(AgentMessage message,
                                                     MessageExecutionRuntimeState state,
                                                     List<PlanStep> steps,
                                                     Supplier<String> approvalModeSupplier) {
                return AgentApplicationService.this.executeRound(message, state, steps, approvalModeSupplier);
            }

            @Override
            public boolean isCanceled(String messageUid, MessageStatus status) {
                return AgentApplicationService.this.isCanceled(messageUid, status);
            }

            @Override
            public boolean isCancellationRequested(String messageUid) {
                return cancellationRegistry.isCanceled(messageUid);
            }

            @Override
            public void cleanupCancellation(String messageUid) {
                cancellationRegistry.clear(messageUid);
            }

            @Override
            public void markMessageRunning(String messageUid) {
                store.updateMessageStatus(messageUid, MessageStatus.RUNNING);
            }

            @Override
            public void completeMessage(AgentMessage message, String answer, int roundsUsed) {
                AgentApplicationService.this.completeMessage(message, answer, roundsUsed);
            }

            @Override
            public void handleLoopLimitReached(String messageUid, MessageExecutionRuntimeState state) {
                AgentApplicationService.this.handleLoopLimitReached(messageUid, state);
            }

            @Override
            public void handleExecutionFailure(String messageUid, Exception ex) {
                store.findMessage(messageUid).ifPresent(message -> failMessage(message, toUserFriendlyFailureMessage(ex, message), ""));
            }

            @Override
            public String defaultApprovalMode() {
                return APPROVAL_MODE_DEFAULT;
            }
        };
    }

    private String toUserFriendlyFailureMessage(Throwable throwable, AgentMessage agentMessage) {
        Throwable root = rootCauseOf(throwable);
        if (root instanceof ConnectException || root instanceof ClosedChannelException) {
            String provider = agentMessage == null ? "" : nullToEmpty(agentMessage.provider()).trim();
            return connectFailureHintByProvider(provider);
        }
        if (root instanceof SocketTimeoutException) {
            return "模型服务响应超时，请稍后重试或检查模型服务状态。";
        }
        if (root instanceof UnknownHostException) {
            return "模型服务地址无法解析，请检查模型服务的主机地址配置。";
        }
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "任务执行失败，请稍后重试。";
        }
        return message;
    }

    private String connectFailureHintByProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if ("ollama".equals(normalized)) {
            return "模型服务连接失败，请检查 Ollama 是否已启动，并确认地址配置正确（默认 http://127.0.0.1:11434）。";
        }
        if ("qwen".equals(normalized) || "dashscope".equals(normalized)) {
            return "模型服务连接失败（Qwen/DashScope）。请检查外网连接、代理设置、API Key 及 API 地址配置是否正确。";
        }
        if ("openai".equals(normalized) || "gemini".equals(normalized) || "kimi".equals(normalized) || "minimax".equals(normalized)) {
            return "模型服务连接失败。请检查外网连接、代理设置、API Key 及 API 地址配置是否正确。";
        }
        return "模型服务连接失败，请检查网络连通性以及模型服务地址配置。";
    }

    private Throwable rootCauseOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private RoundPlanningResult reasonNextAction(AgentMessage message,
                                                 MessageExecutionRuntimeState state,
                                                 ExecutionRuntimeStateStore runtimeStateStore) {
        AgentConversation conversation = requireConversation(message.conversationUid());
        ExecutionScope executionScope = executionScopeResolver.resolveForExecution(conversation, message);
        runtimeStateStore.clearBufferedAnswer(message.messageUid());
        int roundIndex = state.currentRound();
        StringBuilder streamedText = new StringBuilder();
        Planner.StreamReasonResult streamedResult = planner.reasonStream(
                state.memory(),
                executionScope.availableTools(),
                ToolChoice.AUTO,
                executionScope.promptContext(),
                textDelta -> {
                    if (textDelta == null || textDelta.isBlank()) {
                        return;
                    }
                    streamedText.append(textDelta);
                    if (cancellationRegistry.isCanceled(message.messageUid())) {
                        return;
                    }
                    String accumulatedText = streamedText.toString();
                    runtimeStateStore.appendDelta(message.messageUid(), textDelta);
                    publishMessageDelta(message, roundIndex, textDelta, accumulatedText, false);
                }
        );
        ChatResponse response = streamedResult.response();
        recordRoundTokenUsage(message, response, state.currentRound());
        AiMessage aiMessage = response.aiMessage();
        if (aiMessage == null) {
            return RoundPlanningResult.completed("模型未返回有效内容。");
        }

        if (aiMessage.hasToolExecutionRequests()) {
            persistReasoningSnapshot(message, roundIndex, streamedResult.accumulatedText(), aiMessage.text());
        }

        if (hasMeaningfulText(aiMessage.text()) || aiMessage.hasToolExecutionRequests()) {
            state.memory().add(aiMessage);
        }

        if (!aiMessage.hasToolExecutionRequests()) {
            String answer = nullToEmpty(streamedResult.accumulatedText()).trim();
            if (answer.isBlank()) {
                answer = nullToEmpty(aiMessage.text()).trim();
            }
            if (answer.isBlank()) {
                Planner.SummaryResult summaryResult = planner.summarize(
                        state.memory(),
                        "MODEL_RETURNED_EMPTY_ANSWER",
                        state.currentRound(),
                        properties.getLoop().getMaxRounds(),
                        executionScope.promptContext(),
                        executionScope.availableTools()
                );
                recordRoundTokenUsage(message, summaryResult.response(), state.currentRound());
                answer = nullToEmpty(summaryResult.answer()).trim();
            }
            if (streamedResult.streamed() && !answer.isBlank() && !cancellationRegistry.isCanceled(message.messageUid())) {
                publishMessageDelta(message, roundIndex, "", answer, true);
            }
            return RoundPlanningResult.completed(answer);
        }

        runtimeStateStore.clearBufferedAnswer(message.messageUid());
        List<PlanStep> steps = toPlanSteps(message.messageUid(), state.currentRound(), aiMessage.toolExecutionRequests());
        store.saveSteps(message.conversationUid(), message.messageUid(), steps);
        store.updateMessageStatus(message.messageUid(), MessageStatus.PLANNED);
        publishEvent(AgentEventType.PLAN_CREATED, message.conversationUid(), message.messageUid(), null, buildPlanPayload(steps, state.currentRound()));
        return RoundPlanningResult.requiresAction(steps);
    }

    private RoundExecutionResult executeRound(AgentMessage message,
                                              MessageExecutionRuntimeState state,
                                              List<PlanStep> steps,
                                              Supplier<String> approvalModeSupplier) {
        AgentConversation conversation = requireConversation(message.conversationUid());
        ExecutionScope executionScope = executionScopeResolver.resolveForExecution(conversation, message);
        AgentDefinitionEntity executionAgent = executionScope.executionAgent();
        AgentWorkspaceConfig workspaceConfig = executionScope.workspaceConfig();
        Path agentWorkspacePath = workspaceConfig.workspaceDir();
        for (PlanStep step : steps) {
            if (cancellationRegistry.isCanceled(message.messageUid())) {
                return RoundExecutionResult.cancelled();
            }

            PlanStep latestStep = store.findStep(step.stepUid()).orElse(step);
            if (latestStep.status() == StepStatus.COMPLETED) {
                continue;
            }

            ToolPolicyDecisionResult policyDecision = toolExecutionPolicyGateway.evaluateStep(
                    latestStep,
                    agentWorkspacePath,
                    executionAgent == null ? "" : executionAgent.getAgentUid(),
                    executionAgent == null ? NomoClawPaths.DEFAULT_AGENT_NAME : executionAgent.getAgentName(),
                    conversation.channel(),
                    message.conversationUid(),
                    message.messageUid()
            );
            policyDecision = applyApprovalModeOverride(message, policyDecision);
            if (!policyDecision.denied()
                && latestStep.approvalStatus() != ApprovalStatus.APPROVED
                && policyDecision.asks()) {
                store.updateStepApproval(latestStep.stepUid(), ApprovalStatus.PENDING, StepStatus.WAITING_APPROVAL);
                store.updateMessageStatus(message.messageUid(), MessageStatus.WAITING_APPROVAL);
                log.info("[Agent] waiting approval messageUid={} round={}/{} stepUid={} title={}",
                        message.messageUid(),
                        state.currentRound(),
                        properties.getLoop().getMaxRounds(),
                        latestStep.stepUid(),
                        latestStep.title());
                ObjectNode approvalPayload = feedbackBuilder.stepPayload(latestStep, "approval required");
                String approvalDetails = feedbackBuilder.buildStepApprovalDetails(latestStep);
                feedbackBuilder.applyUserFacingFields(approvalPayload, latestStep, "waiting_approval", feedbackBuilder.summaryWaitingApproval(), approvalDetails);
                approvalPayload.put("policyReasonCode", policyDecision.reasonCode().name());
                publishEvent(AgentEventType.STEP_WAITING_APPROVAL, message.conversationUid(), message.messageUid(), latestStep.stepUid(),
                        approvalPayload);
                return RoundExecutionResult.pendingApproval();
            }

            StepExecutionService.RuntimeContext runtimeContext = new StepExecutionService.RuntimeContext(
                    conversation,
                    executionAgent,
                    workspaceConfig,
                    approvalModeSupplier
            );
            StepExecutionService.StepExecutionOutcome outcome = stepExecutionService.executeStepWithRetry(
                    message,
                    latestStep,
                    state.currentRound(),
                    runtimeContext
            );
            if (outcome.canceled()) {
                return RoundExecutionResult.cancelled();
            }
            state.memory().add(ToolExecutionResultMessage.from(
                    toolCallId(latestStep),
                    latestStep.toolName(),
                    outcome.memoryText()
            ));
            if (outcome.injectedMemoryMessage() != null) {
                state.memory().add(outcome.injectedMemoryMessage());
            }
        }

        return RoundExecutionResult.success();
    }

    private List<PlanStep> toPlanSteps(String messageUid, int roundIndex, List<ToolExecutionRequest> toolCalls) {
        List<PlanStep> steps = new ArrayList<>();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return steps;
        }
        if (toolCalls.size() > 1) {
            log.info("[Reasoning] multi tool calls trimmed to single step messageUid={} round={} originalCount={}",
                    messageUid, roundIndex, toolCalls.size());
        }
        int stepIndex = 1;
        ToolExecutionRequest toolCall = toolCalls.get(0);
        JsonNode toolArgs = parseToolArgs(toolCall);
        RiskLevel riskLevel = riskPolicy.evaluateRisk(toolCall.name(), toolArgs);
        steps.add(new PlanStep(
                UuidUtil.newUuid(),
                roundIndex,
                stepIndex,
                feedbackBuilder.buildStepTitle(toolCall.name(), toolArgs),
                toolCall.name(),
                toolArgs,
                riskLevel,
                "",
                StepStatus.CREATED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        ));
        log.info("[Reasoning] tool calls planned messageUid={} round={} count={}", messageUid, roundIndex, steps.size());
        return steps;
    }

    private JsonNode parseToolArgs(ToolExecutionRequest toolCall) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        String rawArgs = toolCall.arguments();
        if (rawArgs != null && !rawArgs.isBlank()) {
            try {
                JsonNode parsed = JsonUtil.fromJson(rawArgs, JsonNode.class);
                if (parsed != null && parsed.isObject()) {
                    node = (ObjectNode) parsed.deepCopy();
                } else {
                    node.put("raw", rawArgs);
                }
            } catch (Exception ex) {
                node.put("raw", rawArgs);
            }
        }
        node.put("_toolCallId", toolCall.id() == null ? UuidUtil.newUuid() : toolCall.id());
        return node;
    }

    private void completeMessage(AgentMessage message, String answer, int roundsUsed) {
        String finalAnswer = answer == null || answer.isBlank() ? feedbackBuilder.messageCompletedDefault() : answer.trim();
        store.updateMessageStatus(message.messageUid(), MessageStatus.COMPLETED);
        persistAssistantReply(message, finalAnswer);
        ObjectNode payload = basePayload("message completed");
        payload.put("status", MessageStatus.COMPLETED.name());
        payload.put("completed", true);
        payload.put("answer", finalAnswer);
        payload.put("roundsUsed", roundsUsed);
        payload.put("maxRounds", properties.getLoop().getMaxRounds());
        payload.put("stopReason", "");
        publishEvent(AgentEventType.MESSAGE_COMPLETED, message.conversationUid(), message.messageUid(), null, payload);
        publishChannelMessageCompletedEvent(message, MessageStatus.COMPLETED, finalAnswer);
        log.info("[Agent] message completed messageUid={} rounds={}/{}", message.messageUid(), roundsUsed, properties.getLoop().getMaxRounds());
    }

    private void handleLoopLimitReached(String messageUid, MessageExecutionRuntimeState state) {
        AgentMessage message = store.findMessage(messageUid)
                .orElseThrow(() -> new IllegalArgumentException("message not found: " + messageUid));
        AgentConversation conversation = requireConversation(message.conversationUid());
        ExecutionScope executionScope = executionScopeResolver.resolveForExecution(conversation, message);
        int roundsUsed = Math.max(1, state.currentRound() - 1);

        ObjectNode loopPayload = basePayload("loop max rounds reached");
        loopPayload.put("status", MessageStatus.FAILED.name());
        loopPayload.put("stopReason", STOP_REASON_MAX_LOOP_REACHED);
        loopPayload.put("roundsUsed", roundsUsed);
        loopPayload.put("maxRounds", properties.getLoop().getMaxRounds());
        publishEvent(AgentEventType.LOOP_LIMIT_REACHED, message.conversationUid(), message.messageUid(), null, loopPayload);

        Planner.SummaryResult summaryResult = planner.summarize(
                state.memory(),
                STOP_REASON_MAX_LOOP_REACHED,
                roundsUsed,
                properties.getLoop().getMaxRounds(),
                executionScope.promptContext(),
                executionScope.availableTools()
        );
        recordRoundTokenUsage(message, summaryResult.response(), roundsUsed);
        failMessage(message, summaryResult.answer(), STOP_REASON_MAX_LOOP_REACHED);
    }

    private AgentConversation requireConversation(String conversationUid) {
        return store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
    }

    private void failMessage(AgentMessage message, String answer, String stopReason) {
        store.updateMessageStatus(message.messageUid(), MessageStatus.FAILED);
        String finalAnswer = answer == null || answer.isBlank() ? feedbackBuilder.messageFailedDefault() : answer.trim();
        persistAssistantReply(message, finalAnswer);
        ObjectNode payload = basePayload(finalAnswer);
        payload.put("status", MessageStatus.FAILED.name());
        payload.put("roundsUsed", currentRound(message.messageUid()));
        payload.put("maxRounds", properties.getLoop().getMaxRounds());
        payload.put("stopReason", nullToEmpty(stopReason));
        publishEvent(AgentEventType.MESSAGE_COMPLETED, message.conversationUid(), message.messageUid(), null, payload);
        publishChannelMessageCompletedEvent(message, MessageStatus.FAILED, finalAnswer);
    }

    private void publishChannelMessageCompletedEvent(AgentMessage message, MessageStatus status, String finalReply) {
        String channel = store.findConversation(message.conversationUid())
                .map(conversation -> conversation.channel() == null ? "" : conversation.channel())
                .orElse("");
        applicationEventPublisher.publishEvent(new ChannelMessageCompletedEvent(
                message.messageUid(),
                message.conversationUid(),
                status,
                finalReply == null ? "" : finalReply,
                channel,
                ""
        ));
    }

    private List<PlanStep> pendingStepsForRound(String messageUid, int roundIndex) {
        return store.listSteps(messageUid, roundIndex).stream()
                .filter(step -> step.status() != StepStatus.COMPLETED)
                .toList();
    }

    /**
     * Event payload / run 视图数据组装（建议后续抽离到 ExecutionFeedbackBuilder）
     */
    private ObjectNode buildPlanPayload(List<PlanStep> steps, int roundIndex) {
        ObjectNode payload = feedbackBuilder.basePayload("tool calls created");
        payload.put("roundIndex", roundIndex);
        payload.set("steps", JsonNodeFactory.instance.arrayNode().addAll(
                steps.stream().map(step -> {
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put("stepUid", step.stepUid());
                    node.put("roundIndex", step.roundIndex());
                    node.put("stepIndex", step.stepIndex());
                    node.put("title", step.title());
                    node.put("toolName", step.toolName());
                    node.set("toolArgs", step.toolArgs() == null ? JsonNodeFactory.instance.objectNode() : step.toolArgs());
                    node.put("riskLevel", step.riskLevel().name());
                    return node;
                }).toList()
        ));
        payload.set("displaySteps", JsonNodeFactory.instance.arrayNode().addAll(
                steps.stream()
                        .map(step -> feedbackBuilder.userFacingStepNode(
                                step,
                                "planned",
                                feedbackBuilder.summaryPlanned(),
                                feedbackBuilder.buildStepPlanDetails(step),
                                null
                        ))
                        .toList()
        ));
        return payload;
    }

    private ObjectNode stepPayload(PlanStep step, String message) {
        ObjectNode payload = basePayload(message);
        payload.put("stepUid", step.stepUid());
        payload.put("roundIndex", step.roundIndex());
        payload.put("stepIndex", step.stepIndex());
        payload.put("title", step.title());
        payload.put("toolName", step.toolName());
        payload.set("toolArgs", step.toolArgs() == null ? JsonNodeFactory.instance.objectNode() : step.toolArgs());
        payload.put("riskLevel", step.riskLevel().name());
        return payload;
    }

    private ObjectNode basePayload(String message) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("message", nullToEmpty(message));
        return payload;
    }

    private void publishMessageDelta(AgentMessage message,
                                     int roundIndex,
                                     String textDelta,
                                     String accumulatedText,
                                     boolean done) {
        ObjectNode payload = basePayload("message delta");
        payload.put("textDelta", textDelta == null ? "" : textDelta);
        payload.put("accumulatedText", accumulatedText == null ? "" : accumulatedText);
        payload.put("roundIndex", roundIndex);
        payload.put("done", done);
        publishTransientEvent(AgentEventType.MESSAGE_DELTA, message.conversationUid(), message.messageUid(), null, payload);
    }

    private void persistReasoningSnapshot(AgentMessage message, int roundIndex, String streamedText, String aiText) {
        String reasoning = nullToEmpty(streamedText).trim();
        if (reasoning.isBlank()) {
            reasoning = nullToEmpty(aiText).trim();
        }
        if (reasoning.isBlank()) {
            return;
        }
        ObjectNode payload = basePayload("message reasoning snapshot");
        payload.put("roundIndex", roundIndex);
        payload.put("content", abbreviate(reasoning, 32 * 1024));
        payload.put("chars", reasoning.length());
        publishEvent(AgentEventType.MESSAGE_REASONING, message.conversationUid(), message.messageUid(), null, payload);
    }

    private void publishEvent(AgentEventType type, String conversationUid, String messageUid, String stepUid, ObjectNode payload) {
        AgentEvent event = new AgentEvent(
                UuidUtil.newUuid(),
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

    private void publishTransientEvent(AgentEventType type, String conversationUid, String messageUid, String stepUid, ObjectNode payload) {
        AgentEvent event = new AgentEvent(
                UuidUtil.newUuid(),
                type,
                conversationUid,
                messageUid,
                stepUid,
                Instant.now(),
                payload
        );
        eventBus.publish(event);
    }

    private void persistAssistantReply(AgentMessage parentMessage, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        store.createAssistantMessage(UuidUtil.newUuid(), parentMessage.conversationUid(), parentMessage.messageUid(), content);
    }

    private void openPathInHostOs(Path file) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(file.toFile());
                return;
            }
        }
        ProcessBuilder processBuilder;
        if (PlatformSupport.isMac()) {
            processBuilder = new ProcessBuilder("open", file.toString());
        } else if (PlatformSupport.isWindows()) {
            processBuilder = new ProcessBuilder("cmd", "/c", "start", "", file.toString());
        } else {
            processBuilder = new ProcessBuilder("xdg-open", file.toString());
        }
        processBuilder.start();
    }

    private boolean hasMeaningfulText(String text) {
        return text != null && !text.isBlank();
    }

    private void recordRoundTokenUsage(AgentMessage message, ChatResponse response, int roundIndex) {
        if (response == null || response.metadata() == null) {
            return;
        }
        TokenUsage usage = response.metadata().tokenUsage();
        String modelName = nullToEmpty(response.metadata().modelName()).trim();
        String provider = llmProperties == null ? "" : nullToEmpty(llmProperties.getProvider()).trim();

        int input = usage == null || usage.inputTokenCount() == null ? 0 : Math.max(0, usage.inputTokenCount());
        int output = usage == null || usage.outputTokenCount() == null ? 0 : Math.max(0, usage.outputTokenCount());
        int total = usage == null || usage.totalTokenCount() == null ? 0 : Math.max(0, usage.totalTokenCount());
        int cachedInput = TokenUsageCacheTokenExtractor.extractCachedInputTokens(usage);
        if (total == 0 && (input > 0 || output > 0)) {
            total = input + output;
        }
        if (input > 0 && cachedInput > input) {
            log.warn("[Agent] cached input token usage exceeded input tokens messageUid={} round={} cachedInput={} input={}",
                    message.messageUid(), roundIndex, cachedInput, input);
            cachedInput = input;
        }

        if (input == 0 && cachedInput == 0 && output == 0 && total == 0 && modelName.isBlank() && provider.isBlank()) {
            return;
        }

        store.accumulateMessageTokenUsage(
                message.messageUid(),
                provider,
                modelName,
                input,
                cachedInput,
                output,
                total
        );

        ObjectNode payload = basePayload("round token usage recorded");
        payload.put("roundIndex", roundIndex);
        payload.put("provider", provider);
        payload.put("modelName", modelName);
        payload.put("inputTokens", input);
        payload.put("cachedInputTokens", cachedInput);
        payload.put("outputTokens", output);
        payload.put("totalTokens", total);
        publishEvent(AgentEventType.ROUND_TOKEN_USAGE, message.conversationUid(), message.messageUid(), null, payload);

        log.info("[Agent] round token usage messageUid={} round={} provider={} model={} input={} cachedInput={} output={} total={}",
                message.messageUid(), roundIndex, provider, modelName, input, cachedInput, output, total);
    }

    private String toolCallId(PlanStep step) {
        String toolCallId = step.toolArgs().path("_toolCallId").asString("");
        return toolCallId.isBlank() ? step.stepUid() : toolCallId;
    }

    private List<ChatMessage> buildConversationMemory(AgentMessage currentMessage) {
        List<AgentMessage> allMessages = store.listMessagesByConversation(currentMessage.conversationUid());
        if (allMessages.isEmpty()) {
            return List.of(toUserMessage(currentMessage));
        }
        int fromIndex = Math.max(0, allMessages.size() - CONVERSATION_CONTEXT_LIMIT);
        List<ChatMessage> memory = new ArrayList<>();
        for (AgentMessage message : allMessages.subList(fromIndex, allMessages.size())) {
            boolean hasString = message.content() != null && !message.content().isBlank();
            boolean hasAttachments = !conversationAttachmentAppService.listByMessageUid(message.messageUid()).isEmpty();
            if (!hasString && !hasAttachments) {
                continue;
            }
            if ("user".equals(message.role())) {
                memory.add(toUserMessage(message));
                continue;
            }
            if ("assistant".equals(message.role())) {
                memory.add(AiMessage.from(message.content()));
            }
        }
        if (memory.isEmpty()) {
            memory.add(toUserMessage(currentMessage));
        }
        log.info("[Agent] conversation context loaded conversationUid={} messageUid={} historyMessages={}",
                currentMessage.conversationUid(), currentMessage.messageUid(), memory.size());
        return memory;
    }

    private UserMessage toUserMessage(AgentMessage message) {
        List<dev.langchain4j.data.message.Content> contents = conversationAttachmentAppService.buildContentsForMessage(
                message.content(),
                message.messageUid()
        );
        if (contents.isEmpty()) {
            return UserMessage.from(message.content());
        }
        return UserMessage.from(contents);
    }

    private boolean isCanceled(String messageUid, MessageStatus status) {
        return status == MessageStatus.CANCELED || cancellationRegistry.isCanceled(messageUid);
    }

    private ToolPolicyDecisionResult applyApprovalModeOverride(AgentMessage message, ToolPolicyDecisionResult decision) {
        if (decision == null || message == null) {
            return decision;
        }
        String approvalMode = messageExecutionOrchestrator.approvalMode(message.messageUid(), APPROVAL_MODE_DEFAULT);
        if (!APPROVAL_MODE_FULL_ACCESS.equals(approvalMode)) {
            return decision;
        }
        // Keep hard guard behavior unchanged even under full access mode.
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

    private ToolPolicyReasonCode resolveStepPolicyReasonCode(ExecutionApprovalScope approvalScope,
                                                             PlanStep step) {
        if (step == null || approvalScope == null || approvalScope.conversation() == null || approvalScope.message() == null) {
            return ToolPolicyReasonCode.NONE;
        }
        AgentConversation conversation = approvalScope.conversation();
        AgentMessage message = approvalScope.message();
        AgentDefinitionEntity executionAgent = approvalScope.executionAgent();
        AgentWorkspaceConfig workspaceConfig = approvalScope.workspaceConfig();
        ToolPolicyDecisionResult decision = toolExecutionPolicyGateway.evaluateStep(
                step,
                workspaceConfig.workspaceDir(),
                executionAgent == null ? "" : executionAgent.getAgentUid(),
                executionAgent == null ? NomoClawPaths.DEFAULT_AGENT_NAME : executionAgent.getAgentName(),
                conversation.channel(),
                message.conversationUid(),
                message.messageUid()
        );
        return decision == null || decision.reasonCode() == null ? ToolPolicyReasonCode.NONE : decision.reasonCode();
    }

    private boolean isHardGuardAskReason(ToolPolicyReasonCode reasonCode) {
        if (reasonCode == null) {
            return false;
        }
        return switch (reasonCode) {
            case HARD_GUARD_SENSITIVE_PATH_READ_ASK,
                 HARD_GUARD_PROTECTED_PATH_ASK,
                 HARD_GUARD_PRIVACY_DEVICE_ASK,
                 HARD_GUARD_SCREEN_CAPTURE_ASK,
                 HARD_GUARD_REMOTE_CONTROL_ASK,
                 HARD_GUARD_PRIVILEGE_ESCALATION_ASK,
                 HARD_GUARD_DATA_EXFILTRATION_ASK -> true;
            default -> false;
        };
    }

    private int currentRound(String messageUid) {
        return store.listSteps(messageUid).stream()
                .mapToInt(PlanStep::roundIndex)
                .max()
                .orElse(1);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
