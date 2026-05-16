package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.command.CreateAgentCommand;
import ai.nomoclaw.bot.application.command.CreateAgentTipCommand;
import ai.nomoclaw.bot.application.command.UpdateAgentBasicInfoCommand;
import ai.nomoclaw.bot.application.dto.*;
import ai.nomoclaw.bot.channel.model.ChannelMessageCompletedEvent;
import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.llm.config.LlmProperties;
import ai.nomoclaw.bot.model.*;
import ai.nomoclaw.bot.planner.Planner;
import ai.nomoclaw.bot.policy.RiskPolicy;
import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecision;
import ai.nomoclaw.bot.policy.tool.ToolPolicyDecisionResult;
import ai.nomoclaw.bot.policy.tool.ToolPolicyReasonCode;
import ai.nomoclaw.bot.policy.tool.permission.PermissionEffect;
import ai.nomoclaw.bot.policy.tool.permission.PermissionScope;
import ai.nomoclaw.bot.policy.tool.permission.PermissionSource;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionApprovalScope;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionRuntimeStateStore;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScope;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScopeResolver;
import ai.nomoclaw.bot.orchestrator.execution.MessageExecutionOrchestrator;
import ai.nomoclaw.bot.orchestrator.execution.MessageExecutionRuntimeState;
import ai.nomoclaw.bot.orchestrator.execution.RoundExecutionResult;
import ai.nomoclaw.bot.orchestrator.execution.RoundPlanningResult;
import ai.nomoclaw.bot.orchestrator.execution.RuntimeModelSelection;
import ai.nomoclaw.bot.orchestrator.execution.StepExecutionService;
import ai.nomoclaw.bot.orchestrator.view.ExecutionFeedbackBuilder;
import ai.nomoclaw.bot.orchestrator.view.RunViewAssembler;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.*;
import ai.nomoclaw.bot.store.repository.*;
import ai.nomoclaw.bot.tool.PlatformSupport;
import ai.nomoclaw.bot.tool.ToolExecutor;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.util.LocalizedMessages;
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
import org.springframework.context.i18n.LocaleContextHolder;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
 * - 运行反馈与展示字段组装由 {@link ExecutionFeedbackBuilder} / {@link RunViewAssembler} 负责
 */
@Service
@Slf4j
public class AgentApplicationService {

    private static final String STOP_REASON_MAX_LOOP_REACHED = "MAX_LOOP_REACHED";
    private static final String APPROVAL_MODE_DEFAULT = "default";
    private static final String APPROVAL_MODE_FULL_ACCESS = "full_access";
    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";
    private static final int CONVERSATION_CONTEXT_LIMIT = 30;

    private final AgentStore store;
    private final Planner planner;
    private final RiskPolicy riskPolicy;
    private final ToolExecutor toolExecutor;
    private final StepReviewer stepReviewer;
    private final AgentEventBus eventBus;
    private final MessageCancellationRegistry cancellationRegistry;
    private final AgentProperties properties;
    private final LlmProperties llmProperties;
    private final AgentGroupDefinitionRepository agentGroupDefinitionRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentSkillRelationRepository agentSkillRelationRepository;
    private final AgentTipApplicationService agentTipApplicationService;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final AgentToolRelationRepository agentToolRelationRepository;
    private final AgentMcpToolRelationRepository agentMcpToolRelationRepository;
    private final ConversationAttachmentAppService conversationAttachmentAppService;
    private final ImageLoaderContextService imageLoaderContextService;
    private final ToolExecutionPolicyGateway toolExecutionPolicyGateway;
    private final ToolPermissionPolicyService toolPermissionPolicyService;
    private final PermissionAppService permissionAppService;
    private final LocalizedMessages localizedMessages;
    private final ExecutionFeedbackBuilder feedbackBuilder;
    private final RunViewAssembler runViewAssembler;
    private final StepExecutionService stepExecutionService;
    private final ExecutionScopeResolver executionScopeResolver;
    private final MessageExecutionOrchestrator messageExecutionOrchestrator;
    private final AgentProfileAppService agentProfileAppService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public AgentApplicationService(AgentStore store,
                                   Planner planner,
                                   RiskPolicy riskPolicy,
                                   ToolExecutor toolExecutor,
                                   StepReviewer stepReviewer,
                                   AgentEventBus eventBus,
                                   MessageCancellationRegistry cancellationRegistry,
                                   AgentProperties properties,
                                   LlmProperties llmProperties,
                                   AgentGroupDefinitionRepository agentGroupDefinitionRepository,
                                   AgentGroupMemberRepository agentGroupMemberRepository,
                                   AgentDefinitionRepository agentDefinitionRepository,
                                   AgentSkillRelationRepository agentSkillRelationRepository,
                                   AgentTipApplicationService agentTipApplicationService,
                                   ToolDefinitionRepository toolDefinitionRepository,
                                   AgentToolRelationRepository agentToolRelationRepository,
                                   AgentMcpToolRelationRepository agentMcpToolRelationRepository,
                                   ConversationAttachmentAppService conversationAttachmentAppService,
                                   ImageLoaderContextService imageLoaderContextService,
                                   ToolExecutionPolicyGateway toolExecutionPolicyGateway,
                                   ToolPermissionPolicyService toolPermissionPolicyService,
                                   PermissionAppService permissionAppService,
                                   LocalizedMessages localizedMessages,
                                   ExecutionFeedbackBuilder feedbackBuilder,
                                   RunViewAssembler runViewAssembler,
                                   StepExecutionService stepExecutionService,
                                   ExecutionScopeResolver executionScopeResolver,
                                   MessageExecutionOrchestrator messageExecutionOrchestrator,
                                   AgentProfileAppService agentProfileAppService,
                                   ApplicationEventPublisher applicationEventPublisher) {
        this.store = store;
        this.planner = planner;
        this.riskPolicy = riskPolicy;
        this.toolExecutor = toolExecutor;
        this.stepReviewer = stepReviewer;
        this.eventBus = eventBus;
        this.cancellationRegistry = cancellationRegistry;
        this.properties = properties;
        this.llmProperties = llmProperties;
        this.agentGroupDefinitionRepository = agentGroupDefinitionRepository;
        this.agentGroupMemberRepository = agentGroupMemberRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentSkillRelationRepository = agentSkillRelationRepository;
        this.agentTipApplicationService = agentTipApplicationService;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.agentToolRelationRepository = agentToolRelationRepository;
        this.agentMcpToolRelationRepository = agentMcpToolRelationRepository;
        this.conversationAttachmentAppService = conversationAttachmentAppService;
        this.imageLoaderContextService = imageLoaderContextService;
        this.toolExecutionPolicyGateway = toolExecutionPolicyGateway;
        this.toolPermissionPolicyService = toolPermissionPolicyService;
        this.permissionAppService = permissionAppService;
        this.localizedMessages = localizedMessages;
        this.feedbackBuilder = feedbackBuilder;
        this.runViewAssembler = runViewAssembler;
        this.stepExecutionService = stepExecutionService;
        this.executionScopeResolver = executionScopeResolver;
        this.messageExecutionOrchestrator = messageExecutionOrchestrator;
        this.agentProfileAppService = agentProfileAppService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public String createConversation(String agentGroupUid, String agentUid) {
        return createConversation(agentGroupUid, agentUid, "web");
    }

    public String createConversation(String agentGroupUid, String agentUid, String channel) {
        String conversationUid = UUID.randomUUID().toString();
        String normalizedGroupUid = normalizeAgentGroupUid(agentGroupUid);
        String normalizedAgentUid = normalizeOptionalAgentUid(agentUid);
        String normalizedChannel = channel == null || channel.isBlank() ? "web" : channel.trim();
        store.createConversation(
                conversationUid,
                normalizedGroupUid,
                normalizedAgentUid,
                normalizedChannel
        );
        log.info("[Agent] conversation created conversationUid={} agentGroupUid={} agentUid={} channel={}",
                conversationUid, normalizedGroupUid, normalizedAgentUid, normalizedChannel);
        return conversationUid;
    }

    public List<ConversationSummaryDto> listConversations() {
        return store.listConversations().stream()
                .map(conversation -> new ConversationSummaryDto(
                        conversation.conversationUid(),
                        conversation.agentGroupUid(),
                        conversation.agentUid(),
                        conversation.title(),
                        conversation.pinned(),
                        conversation.createdAt(),
                        conversation.updatedAt()
                ))
                .toList();
    }

    public List<AgentCatalogGroupDto> listAgentGroups() {
        return agentProfileAppService.listAgentGroups();
    }

    public List<ConversationMessageDto> listMessages(String conversationUid) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        List<AgentMessage> messages = store.listMessagesByConversation(conversationUid);
        Map<String, List<ConversationAttachmentDto>> attachmentsByMessage = conversationAttachmentAppService.listByMessageUids(
                messages.stream().map(AgentMessage::messageUid).toList()
        );
        return messages.stream()
                .map(message -> new ConversationMessageDto(
                        message.messageUid(),
                        emptyToNull(message.parentMessageUid()),
                        message.role(),
                        message.content(),
                        message.status(),
                        message.provider(),
                        message.modelName(),
                        message.inputTokens(),
                        message.outputTokens(),
                        message.totalTokens(),
                        message.createdAt(),
                        buildMessageFileLinks(message),
                        attachmentsByMessage.getOrDefault(message.messageUid(), List.of())
                ))
                .toList();
    }

    public AgentCatalogAgentDto createAgent(CreateAgentCommand request) {
        return agentProfileAppService.createAgent(request);
    }

    public AgentCatalogAgentDto updateAgentBasicInfo(String agentUid, UpdateAgentBasicInfoCommand request) {
        return agentProfileAppService.updateAgentBasicInfo(agentUid, request);
    }

    public List<AgentToolDto> listAgentTools(String agentUid) {
        return agentProfileAppService.listAgentTools(agentUid);
    }

    public AgentToolDto updateAgentToolStatus(String agentUid, String toolKey, boolean enabled) {
        return agentProfileAppService.updateAgentToolStatus(agentUid, toolKey, enabled);
    }

    public List<AgentDocDto> listAgentDocs(String agentUid) {
        return agentProfileAppService.listAgentDocs(agentUid);
    }

    public AgentDocDto updateAgentDoc(String agentUid, String docKey, String content) {
        return agentProfileAppService.updateAgentDoc(agentUid, docKey, content);
    }

    public List<AgentTipDto> listAgentTips(String agentUid) {
        return agentTipApplicationService.listAgentTips(agentUid);
    }

    public AgentTipDto createAgentTip(String agentUid, CreateAgentTipCommand request) {
        return agentTipApplicationService.createAgentTip(agentUid, request);
    }

    public void deleteAgentTip(String agentUid, String tipUid) {
        agentTipApplicationService.deleteAgentTip(agentUid, tipUid);
    }

    public List<ConversationMessageRunDto> listMessageRuns(String conversationUid) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        return store.listMessagesByConversation(conversationUid).stream()
                .filter(message -> "user".equals(message.role()))
                .map(message -> runViewAssembler.toMessageRunResponse(
                        message,
                        store.listSteps(message.messageUid()),
                        store.listEventsByMessage(message.messageUid())
                ))
                .filter(Objects::nonNull)
                .toList();
    }

    public void deleteConversation(String conversationUid) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        List<AgentMessage> messages = store.listMessagesByConversation(conversationUid);
        messages.forEach(message -> {
            cancellationRegistry.cancel(message.messageUid());
            messageExecutionOrchestrator.clearRuntime(message.messageUid());
        });
        conversationAttachmentAppService.purgeConversationAttachments(conversationUid);
        store.deleteConversation(conversationUid);
        log.info("[Agent] conversation deleted conversationUid={} agentGroupUid={} agentUid={}",
                conversationUid, conversation.agentGroupUid(), conversation.agentUid());
    }

    public void deleteAgent(String agentUid) {
        agentProfileAppService.deleteAgent(agentUid);
    }

    public void updateConversationTitle(String conversationUid, String title) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        store.updateConversationTitle(conversationUid, normalizedTitle);
        log.info("[Agent] conversation title updated conversationUid={} title={} agentGroupUid={} agentUid={}",
                conversationUid, normalizedTitle, conversation.agentGroupUid(), conversation.agentUid());
    }

    public void updateConversationPinned(String conversationUid, boolean pinned) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        store.updateConversationPinned(conversationUid, pinned);
        log.info("[Agent] conversation pin updated conversationUid={} pinned={} agentGroupUid={} agentUid={}",
                conversationUid, pinned, conversation.agentGroupUid(), conversation.agentUid());
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
        String normalizedChannel = channel == null || channel.isBlank() ? "web" : channel.trim();
        String normalizedApprovalMode = normalizeApprovalMode(approvalMode);
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseGet(() -> store.createConversation(conversationUid, "", DEFAULT_AGENT_UID, normalizedChannel));
        RuntimeModelSelection runtimeModel = executionScopeResolver.resolveForMessageSubmission(conversation, modelProvider, modelName);
        String messageUid = UUID.randomUUID().toString();
        int maxRounds = properties.getLoop().getMaxRounds();
        store.createUserMessage(
                messageUid,
                conversationUid,
                message,
                maxRounds,
                runtimeModel.modelProvider(),
                runtimeModel.modelName()
        );
        conversationAttachmentAppService.attachUploadsToMessage(
                conversationUid,
                messageUid,
                fileUrls,
                runtimeModel.modelProvider(),
                runtimeModel.modelName()
        );
        if (conversation.title() == null || conversation.title().isBlank()) {
            store.updateConversationTitle(conversationUid, buildConversationTitle(message));
        }
        log.info("[Agent] message created conversationUid={} messageUid={} channel={} model={}/{} maxRounds={} message={}",
                conversationUid, messageUid, normalizedChannel, runtimeModel.modelProvider(), runtimeModel.modelName(), maxRounds, summarize(message));
        if (beforeExecuteHook != null) {
            beforeExecuteHook.accept(messageUid);
        }
        messageExecutionOrchestrator.enqueue(messageUid, LocaleContextHolder.getLocale(), normalizedApprovalMode, executionDriver());
        return messageUid;
    }

    public AgentMessage getMessage(String messageUid) {
        return store.findMessage(messageUid)
                .orElseThrow(() -> new IllegalArgumentException("message not found: " + messageUid));
    }

    public int maxLoopRounds() {
        return properties.getLoop().getMaxRounds();
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

    public void approveStep(String conversationUid, String stepUid) {
        decideStep(conversationUid, stepUid, "allow", PermissionScope.ONCE, "");
    }

    public void rejectStep(String conversationUid, String stepUid) {
        decideStep(conversationUid, stepUid, "deny", PermissionScope.ONCE, "");
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
                                                     String approvalMode) {
                return AgentApplicationService.this.executeRound(message, state, steps, approvalMode);
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
                                              String approvalMode) {
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
                    approvalMode
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

    /**
     * 执行单个步骤并处理重试决策。
     *
     * <p>决策依据来自 StepReviewer：
     * - passed: 步骤完成
     * - retryableFailure: 进入下一次 attempt
     * - failure: 立即结束该步骤
     */
    private StepExecutionOutcome executeStepWithRetry(AgentMessage message, PlanStep step, int roundIndex) {
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
            ObjectNode startedPayload = stepPayload(step, "attempt " + currentAttempt);
            applyUserFacingFields(startedPayload, step, "running", i18n("agent.step.summary.running"), buildStepStartedDetails(step));
            publishEvent(AgentEventType.STEP_STARTED, message.conversationUid(), message.messageUid(), step.stepUid(),
                    startedPayload);

            ToolResult result = safeExecuteTool(message, step, roundIndex, currentAttempt);
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
                        resultPayload(step, result, currentAttempt, properties.getLoop().getMaxRounds()));
                return StepExecutionOutcome.success(result, buildToolResultMessage(step, result, true, ""));
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
            ObjectNode failedPayload = resultPayload(step, result, currentAttempt, properties.getLoop().getMaxRounds());
            failedPayload.put("retryable", hasNextAttempt);
            publishEvent(AgentEventType.STEP_FAILED, message.conversationUid(), message.messageUid(), step.stepUid(), failedPayload);
            if (!hasNextAttempt) {
                return StepExecutionOutcome.failure(result, buildToolResultMessage(step, result, false, decision.message()));
            }
        }

        ToolResult exhausted = lastResult != null
                ? lastResult
                : ToolResult.failure("RETRY_EXHAUSTED", "step retry exhausted", JsonNodeFactory.instance.objectNode());
        return StepExecutionOutcome.failure(exhausted, buildToolResultMessage(step, exhausted, false, lastDecisionMessage));
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

    /**
     * 工具执行安全入口。
     *
     * <p>这里集中放“执行前硬策略”：
     * - 例如 cron channel 下禁止再次创建 cron 任务
     * - 其他 tool policy 也建议统一收敛到此处
     *
     * <p>后续可抽取为 ToolExecutionPolicyChain + ToolExecutionGateway。
     */
    private ToolResult safeExecuteTool(AgentMessage message, PlanStep step, int roundIndex, int currentAttempt) {
        try {
            AgentConversation conversation = requireConversation(message.conversationUid());
            // Runtime guard: even if a stale plan contains cron management tools, do not manage schedules in cron-triggered runs.
            if (isCronTool(step.toolName()) && isCronChannel(conversation.channel())) {
                ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
                artifacts.put("skipped", true);
                artifacts.put("reason", "cron management tools are disabled during scheduled executions");
                ObjectNode metrics = JsonNodeFactory.instance.objectNode();
                metrics.put("skipped", true);
                return ToolResult.success("定时触发执行时已禁止管理定时任务。", artifacts, metrics);
            }
            ExecutionScope executionScope = executionScopeResolver.resolveForExecution(conversation, message);
            AgentDefinitionEntity agent = executionScope.executionAgent();
            AgentWorkspaceConfig workspaceConfig = executionScope.workspaceConfig();
            Path agentWorkspacePath = workspaceConfig.workspaceDir();
            ToolPolicyDecisionResult policyDecision = toolExecutionPolicyGateway.evaluateStep(
                    step,
                    agentWorkspacePath,
                    agent == null ? "" : agent.getAgentUid(),
                    agent == null ? NomoClawPaths.DEFAULT_AGENT_NAME : agent.getAgentName(),
                    conversation.channel(),
                    message.conversationUid(),
                    message.messageUid()
            );
            policyDecision = applyApprovalModeOverride(message, policyDecision);
            if (policyDecision.denied()) {
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
            // If the step has already been approved by user, do not block it again here.
            // The approval gate is handled in executeRound before step execution.
            if (policyDecision.asks() && step.approvalStatus() != ApprovalStatus.APPROVED) {
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
                    agent == null ? "" : agent.getAgentUid(),
                    agent == null ? "" : agent.getAgentName(),
                    workspaceConfig.workspaceDir(),
                    workspaceConfig.tmpDir(),
                    workspaceConfig.reportDir(),
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

    private void publishStepProgress(AgentMessage message,
                                     PlanStep step,
                                     int roundIndex,
                                     int currentAttempt,
                                     ToolProgress progress) {
        if (progress == null) {
            return;
        }
        ObjectNode payload = stepPayload(step, "attempt " + currentAttempt + " progress");
        String summary = hasMeaningfulText(progress.summary()) ? progress.summary().trim() : i18n("agent.step.summary.running");
        String details = hasMeaningfulText(progress.details()) ? progress.details().trim() : summary;
        applyUserFacingFields(payload, step, "running", summary, details);
        payload.put("roundIndex", roundIndex);
        payload.put("silentLog", true);
        if (progress.metrics() != null && !progress.metrics().isNull()) {
            payload.set("progressMetrics", progress.metrics());
        }
        publishEvent(AgentEventType.STEP_STARTED, message.conversationUid(), message.messageUid(), step.stepUid(), payload);
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

    private List<PlanStep> toPlanSteps(String messageUid, int roundIndex, List<ToolExecutionRequest> toolCalls) {
        List<PlanStep> steps = new ArrayList<>();
        int stepIndex = 1;
        for (ToolExecutionRequest toolCall : toolCalls) {
            JsonNode toolArgs = parseToolArgs(toolCall);
            RiskLevel riskLevel = riskPolicy.evaluateRisk(toolCall.name(), toolArgs);
            steps.add(new PlanStep(
                    UUID.randomUUID().toString(),
                    roundIndex,
                    stepIndex++,
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
        }
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
        node.put("_toolCallId", toolCall.id() == null ? UUID.randomUUID().toString() : toolCall.id());
        return node;
    }

    // ===== Step display 文案与计划信息组装（建议后续抽离到 ExecutionFeedbackBuilder） =====

    private String buildStepTitle(String toolName, JsonNode toolArgs) {
        return switch (nullToEmpty(toolName)) {
            case "CommandTool" -> {
                String command = toolArgs.path("command").asString("");
                yield command.isBlank() ? i18n("agent.step.title.command") : i18n("agent.step.title.command.withArg", abbreviate(command, 96));
            }
            case "BrowserTool" -> {
                String action = toolArgs.path("action").asString("");
                String target = toolArgs.path("selector").asString("");
                if (target.isBlank()) {
                    target = toolArgs.path("url").asString("");
                }
                yield i18n(
                        target.isBlank() ? "agent.step.title.browser" : "agent.step.title.browser.withTarget",
                        action.isBlank() ? i18n("agent.step.title.browser.defaultAction") : action,
                        abbreviate(target, 48)
                );
            }
            case "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool" -> {
                String action = switch (nullToEmpty(toolName)) {
                    case "ReadFileTool" -> "read";
                    case "ListFileTool" -> "list";
                    case "CreateFileTool" -> toolArgs.path("mode").asString("create_or_truncate");
                    case "EditFileTool" -> "edit";
                    default -> "";
                };
                String path = toolArgs.path("path").asString("");
                yield i18n(
                        path.isBlank() ? "agent.step.title.file" : "agent.step.title.file.withPath",
                        action.isBlank() ? i18n("agent.step.title.file.defaultAction") : action,
                        abbreviate(path, 48)
                );
            }
            case "CronCreateTool" -> {
                String task = toolArgs.path("task").asString("");
                yield task.isBlank() ? i18n("agent.step.title.cron.create") : i18n("agent.step.title.cron.create.withTask", abbreviate(task, 48));
            }
            case "CronDeleteTool" -> {
                String jobUid = toolArgs.path("jobUid").asString("");
                yield jobUid.isBlank() ? i18n("agent.step.title.cron.delete") : i18n("agent.step.title.cron.delete.withId", abbreviate(jobUid, 48));
            }
            case "CronListTool" -> i18n("agent.step.title.cron.list");
            case "ImageLoaderTool" -> {
                String reference = toolArgs.path("reference").asString("");
                yield reference.isBlank() ? i18n("agent.step.title.image") : i18n("agent.step.title.image.withRef", abbreviate(reference, 48));
            }
            case "WebSearchTool" -> {
                String query = toolArgs.path("query").asString("");
                yield query.isBlank() ? i18n("agent.step.title.web.search") : i18n("agent.step.title.web.search.withQuery", abbreviate(query, 48));
            }
            case "WebFetchTool" -> {
                String url = toolArgs.path("url").asString("");
                yield url.isBlank() ? i18n("agent.step.title.web.fetch") : i18n("agent.step.title.web.fetch.withUrl", abbreviate(url, 48));
            }
            default -> i18n("agent.step.title.tool.default", nullToEmpty(toolName));
        };
    }

    private String buildDisplayTitle(PlanStep step) {
        JsonNode toolArgs = step.toolArgs();
        return switch (nullToEmpty(step.toolName())) {
            case "CommandTool" -> {
                String command = toolArgs.path("command").asString("");
                yield command.isBlank() ? i18n("agent.step.display.command.running") : i18n("agent.step.display.command.running.withCommand", command);
            }
            case "BrowserTool" -> switch (toolArgs.path("action").asString("")) {
                case "open", "navigate" -> i18n("agent.step.display.browser.open");
                case "click" -> i18n("agent.step.display.browser.click");
                case "type" -> i18n("agent.step.display.browser.type");
                case "extract_text" -> i18n("agent.step.display.browser.extractText");
                case "screenshot" -> i18n("agent.step.display.browser.screenshot");
                case "download" -> i18n("agent.step.display.browser.download");
                case "wait_for" -> i18n("agent.step.display.browser.waitFor");
                case "press_key" -> i18n("agent.step.display.browser.pressKey");
                case "snapshot" -> i18n("agent.step.display.browser.snapshot");
                default -> i18n("agent.step.display.browser.default");
            };
            case "ReadFileTool" -> i18n("agent.step.display.file.read");
            case "ListFileTool" -> i18n("agent.step.display.file.list");
            case "CreateFileTool" -> i18n("agent.step.display.file.create");
            case "EditFileTool" -> i18n("agent.step.display.file.edit");
            case "CronCreateTool" -> i18n("agent.step.display.cron.create");
            case "CronDeleteTool" -> i18n("agent.step.display.cron.delete");
            case "CronListTool" -> i18n("agent.step.display.cron.list");
            case "ImageLoaderTool" -> i18n("agent.step.display.image");
            case "DesktopScreenshotTool" -> i18n("agent.step.display.desktopScreenshot");
            case "FileSearchTool" -> i18n("agent.step.display.fileSearch");
            case "WebSearchTool" -> i18n("agent.step.display.web.search");
            case "WebFetchTool" -> i18n("agent.step.display.web.fetch");
            default -> step.title() == null || step.title().isBlank() ? i18n("agent.step.display.default") : step.title();
        };
    }

    private String buildStepPlanDetails(PlanStep step) {
        return switch (nullToEmpty(step.toolName())) {
            case "CommandTool" -> {
                String command = step.toolArgs().path("command").asString("");
                String cwd = step.toolArgs().path("cwd").asString("");
                yield command.isBlank()
                        ? i18n("agent.step.plan.command.planned")
                        : (cwd.isBlank()
                            ? i18n("agent.step.plan.command.withCommand", command)
                            : i18n("agent.step.plan.command.withCommandAndCwd", command, cwd));
            }
            case "BrowserTool" -> {
                String action = step.toolArgs().path("action").asString("");
                String url = step.toolArgs().path("url").asString("");
                String selector = step.toolArgs().path("selector").asString("");
                String target = !url.isBlank() ? abbreviate(url, 72) : abbreviate(selector, 48);
                yield target.isBlank()
                        ? i18n("agent.step.plan.browser.planned")
                        : i18n("agent.step.plan.browser.withTarget",
                        action.isBlank() ? i18n("agent.step.title.browser.defaultAction") : action,
                        target);
            }
            case "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool" -> {
                String action = switch (nullToEmpty(step.toolName())) {
                    case "ReadFileTool" -> i18n("agent.step.plan.file.action.read");
                    case "ListFileTool" -> i18n("agent.step.plan.file.action.list");
                    case "CreateFileTool" -> {
                        String mode = step.toolArgs().path("mode").asString("create_or_truncate");
                        yield "append".equals(mode) ? i18n("agent.step.plan.file.action.append") : i18n("agent.step.plan.file.action.write");
                    }
                    case "EditFileTool" -> i18n("agent.step.plan.file.action.edit");
                    default -> i18n("agent.step.plan.file.action.default");
                };
                String path = step.toolArgs().path("path").asString("");
                yield path.isBlank()
                        ? i18n("agent.step.plan.file.planned")
                        : i18n("agent.step.plan.file.withPath", action.isBlank() ? i18n("agent.step.plan.file.action.default") : action, abbreviate(path, 72));
            }
            case "CronCreateTool" -> {
                String task = step.toolArgs().path("task").asString("");
                yield task.isBlank() ? i18n("agent.step.plan.cron.create") : i18n("agent.step.plan.cron.create.withTask", abbreviate(task, 72));
            }
            case "CronDeleteTool" -> {
                String jobUid = step.toolArgs().path("jobUid").asString("");
                yield jobUid.isBlank() ? i18n("agent.step.plan.cron.delete") : i18n("agent.step.plan.cron.delete.withId", abbreviate(jobUid, 72));
            }
            case "CronListTool" -> {
                String jobUid = step.toolArgs().path("jobUid").asString("");
                yield jobUid.isBlank() ? i18n("agent.step.plan.cron.list") : i18n("agent.step.plan.cron.list.withId", abbreviate(jobUid, 72));
            }
            case "ImageLoaderTool" -> {
                String reference = step.toolArgs().path("reference").asString("");
                yield reference.isBlank()
                        ? i18n("agent.step.plan.image")
                        : i18n("agent.step.plan.image.withRef", abbreviate(reference, 72));
            }
            case "WebSearchTool" -> {
                String query = step.toolArgs().path("query").asString("");
                yield query.isBlank()
                        ? i18n("agent.step.plan.web.search")
                        : i18n("agent.step.plan.web.search.withQuery", abbreviate(query, 72));
            }
            case "WebFetchTool" -> {
                String url = step.toolArgs().path("url").asString("");
                yield url.isBlank()
                        ? i18n("agent.step.plan.web.fetch")
                        : i18n("agent.step.plan.web.fetch.withUrl", abbreviate(url, 72));
            }
            default -> i18n("agent.step.plan.default");
        };
    }

    private String buildStepStartedDetails(PlanStep step) {
        return switch (nullToEmpty(step.toolName())) {
            case "CommandTool", "BrowserTool", "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool", "WebSearchTool", "WebFetchTool", "CronCreateTool", "CronDeleteTool", "CronListTool" -> buildStepPlanDetails(step)
                    .replace(i18n("agent.step.plan.systemPreparing"), i18n("agent.step.started.systemRunning"))
                    .replace(i18n("agent.step.plan.systemPlanned"), i18n("agent.step.started.systemRunning"));
            default -> i18n("agent.step.started.default");
        };
    }

    private String buildStepApprovalDetails(PlanStep step) {
        return i18n("agent.step.approval.prompt", formatApprovalAction(step.toolName(), step.toolArgs()));
    }

    private String buildStepSuccessDetails(PlanStep step, ToolResult result) {
        return switch (nullToEmpty(step.toolName())) {
            case "BrowserTool" -> {
                String path = result.artifacts() == null ? "" : result.artifacts().path("path").asString("");
                if (!path.isBlank()) {
                    yield i18n("agent.step.success.browser.saved", path);
                }
                yield hasMeaningfulText(result.output()) ? i18n("agent.step.success.browser.withOutput", abbreviate(result.output(), 120)) : i18n("agent.step.success.browser");
            }
            case "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool" -> {
                String path = result.artifacts() == null ? "" : result.artifacts().path("path").asString("");
                yield path.isBlank() ? i18n("agent.step.success.file") : i18n("agent.step.success.file.withPath", abbreviate(path, 96));
            }
            case "CommandTool" -> {
                String command = step.toolArgs().path("command").asString("");
                String stdout = result.artifacts() == null ? "" : result.artifacts().path("stdout").asString("");
                String stderr = result.artifacts() == null ? "" : result.artifacts().path("stderr").asString("");
                String outputBody = hasMeaningfulText(stdout) ? stdout : (hasMeaningfulText(result.output()) ? result.output() : stderr);
                String prefix = command.isBlank() ? i18n("agent.step.success.command") : i18n("agent.step.success.command.withCommand", command);
                if (!hasMeaningfulText(outputBody)) {
                    yield prefix;
                }
                yield i18n("agent.step.success.command.withOutput", prefix, abbreviate(outputBody, 1200));
            }
            case "CronCreateTool" -> i18n("agent.step.success.cron.create");
            case "CronDeleteTool" -> i18n("agent.step.success.cron.delete");
            case "CronListTool" -> i18n("agent.step.success.cron.list");
            case "ImageLoaderTool" -> {
                int resolved = result.artifacts() == null ? 0 : result.artifacts().path("resolvedCount").asInt(0);
                boolean matched = result.artifacts() != null && result.artifacts().path("matched").asBoolean(false);
                if (!matched || resolved <= 0) {
                    yield i18n("agent.step.success.image.noMatch");
                }
                yield i18n("agent.step.success.image.withCount", resolved);
            }
            case "WebSearchTool" -> i18n("agent.step.success.web.search");
            case "WebFetchTool" -> i18n("agent.step.success.web.fetch");
            default -> hasMeaningfulText(result.output()) ? abbreviate(result.output(), 140) : i18n("agent.step.success.default");
        };
    }

    private String buildStepFailureDetails(PlanStep step, ToolResult result) {
        String message = hasMeaningfulText(result.errorMessage()) ? result.errorMessage() : result.output();
        if (!hasMeaningfulText(message)) {
            message = i18n("agent.step.failure.default");
        }
        if ("CommandTool".equals(nullToEmpty(step.toolName()))) {
            String command = step.toolArgs().path("command").asString("");
            String stderr = result.artifacts() == null ? "" : result.artifacts().path("stderr").asString("");
            String suffix = hasMeaningfulText(stderr) ? "\nstderr:\n" + abbreviate(stderr, 800) : "";
            if (!command.isBlank()) {
                return abbreviate(i18n("agent.step.failure.command", command, message) + suffix, 1600);
            }
        }
        return abbreviate(message, 180);
    }

    private String formatApprovalAction(String toolName, JsonNode toolArgs) {
        return switch (nullToEmpty(toolName)) {
            case "BrowserTool" -> {
                String action = toolArgs.path("action").asString("");
                String url = toolArgs.path("url").asString("");
                String selector = toolArgs.path("selector").asString("");
                String text = toolArgs.path("text").asString("");
                if ("open".equals(action) || "navigate".equals(action)) {
                    yield i18n("agent.step.approval.browser.open", url.isBlank() ? i18n("agent.step.approval.notProvided.url") : abbreviate(url, 96));
                }
                if ("click".equals(action)) {
                    yield i18n("agent.step.approval.browser.click", selector.isBlank() ? i18n("agent.step.approval.notProvided.target") : abbreviate(selector, 72));
                }
                if ("type".equals(action)) {
                    yield text.isBlank()
                            ? i18n("agent.step.approval.browser.type", selector.isBlank() ? i18n("agent.step.approval.notProvided.target") : abbreviate(selector, 72))
                            : i18n("agent.step.approval.browser.type.withText",
                            selector.isBlank() ? i18n("agent.step.approval.notProvided.target") : abbreviate(selector, 72),
                            abbreviate(text, 60));
                }
                if ("screenshot".equals(action)) {
                    String output = toolArgs.path("output").asString("");
                    yield output.isBlank()
                            ? i18n("agent.step.approval.browser.screenshot.default")
                            : i18n("agent.step.approval.browser.screenshot.output", abbreviate(output, 96));
                }
                yield i18n("agent.step.approval.browser.default");
            }
            case "CommandTool" -> {
                String command = toolArgs.path("command").asString("");
                String cwd = toolArgs.path("cwd").asString("");
                String renderedCommand = command.isBlank() ? i18n("agent.step.approval.notProvided.command") : command;
                yield cwd.isBlank()
                        ? i18n("agent.step.approval.command", renderedCommand)
                        : i18n("agent.step.approval.command.withCwd", renderedCommand, cwd);
            }
            case "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool" -> {
                String action = switch (nullToEmpty(toolName)) {
                    case "ReadFileTool" -> i18n("agent.step.plan.file.action.read");
                    case "ListFileTool" -> i18n("agent.step.plan.file.action.list");
                    case "CreateFileTool" -> {
                        String mode = toolArgs.path("mode").asString("create_or_truncate");
                        yield "append".equals(mode) ? i18n("agent.step.plan.file.action.append") : i18n("agent.step.plan.file.action.write");
                    }
                    case "EditFileTool" -> i18n("agent.step.plan.file.action.edit");
                    default -> i18n("agent.step.plan.file.action.default");
                };
                String path = toolArgs.path("path").asString("");
                yield path.isBlank()
                        ? i18n("agent.step.approval.file", action)
                        : i18n("agent.step.approval.file.withPath", action, abbreviate(path, 96));
            }
            case "CronCreateTool" -> {
                String task = toolArgs.path("task").asString("");
                yield task.isBlank() ? i18n("agent.step.approval.cron.create") : i18n("agent.step.approval.cron.create.withTask", abbreviate(task, 72));
            }
            case "CronDeleteTool" -> {
                String jobUid = toolArgs.path("jobUid").asString("");
                yield jobUid.isBlank() ? i18n("agent.step.approval.cron.delete") : i18n("agent.step.approval.cron.delete.withId", abbreviate(jobUid, 72));
            }
            case "CronListTool" -> i18n("agent.step.approval.cron.list");
            case "WebSearchTool" -> {
                String query = toolArgs.path("query").asString("");
                yield query.isBlank() ? i18n("agent.step.approval.web.search") : i18n("agent.step.approval.web.search.withQuery", abbreviate(query, 72));
            }
            case "WebFetchTool" -> {
                String url = toolArgs.path("url").asString("");
                yield url.isBlank() ? i18n("agent.step.approval.web.fetch") : i18n("agent.step.approval.web.fetch.withUrl", abbreviate(url, 96));
            }
            default -> i18n("agent.step.approval.default");
        };
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

    private long resolveTimeoutMs(String toolName) {
        String normalizedToolName = nullToEmpty(toolName);
        if ("BrowserTool".equals(normalizedToolName)) {
            return properties.getBrowser().getStepTimeoutSeconds() * 1000L;
        }
        return properties.getCommand().getTimeoutSeconds() * 1000L;
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

    private void applyUserFacingFields(ObjectNode payload,
                                       PlanStep step,
                                       String status,
                                       String displaySummary,
                                       String displayDetails) {
        payload.put("status", status);
        payload.put("displayTitle", buildDisplayTitle(step));
        payload.put("displaySummary", nullToEmpty(displaySummary));
        payload.put("displayDetails", nullToEmpty(displayDetails));
    }

    private ObjectNode userFacingStepNode(PlanStep step,
                                          String status,
                                          String displaySummary,
                                          String displayDetails,
                                          Instant updatedTime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("stepUid", step.stepUid());
        node.put("roundIndex", step.roundIndex());
        node.put("stepIndex", step.stepIndex());
        node.put("status", status);
        node.put("toolName", nullToEmpty(step.toolName()));
        node.set("toolArgs", step.toolArgs() == null ? JsonNodeFactory.instance.objectNode() : step.toolArgs());
        node.put("displayTitle", buildDisplayTitle(step));
        node.put("displaySummary", nullToEmpty(displaySummary));
        node.put("displayDetails", nullToEmpty(displayDetails));
        node.put("updatedTime", updatedTime == null ? "" : updatedTime.toString());
        return node;
    }

    private ObjectNode resultPayload(PlanStep step, ToolResult result, int attempt, int maxRounds) {
        ObjectNode payload = stepPayload(step, result.success() ? "success" : "failed");
        payload.put("round", step.roundIndex());
        payload.put("maxRounds", maxRounds);
        payload.put("attempt", attempt);
        payload.put("success", result.success());
        payload.put("output", nullToEmpty(result.output()));
        payload.put("errorCode", nullToEmpty(result.errorCode()));
        payload.put("errorMessage", nullToEmpty(result.errorMessage()));
        payload.set("metrics", result.metrics() == null ? JsonNodeFactory.instance.objectNode() : result.metrics());
        payload.set("artifacts", result.artifacts() == null ? JsonNodeFactory.instance.objectNode() : result.artifacts());
        if (result.success()) {
            applyUserFacingFields(payload, step, "completed", i18n("agent.step.summary.completed"), buildStepSuccessDetails(step, result));
        } else {
            applyUserFacingFields(payload, step, "failed", i18n("agent.step.summary.failed"), buildStepFailureDetails(step, result));
        }
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
        publishEvent(AgentEventType.MESSAGE_DELTA, message.conversationUid(), message.messageUid(), null, payload);
    }

    private void publishEvent(AgentEventType type, String conversationUid, String messageUid, String stepUid, ObjectNode payload) {
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
//        log.info("[AgentEvent] type={} conversationUid={} messageUid={} stepUid={} payload={}",
//                type, conversationUid, messageUid, stepUid, summarize(payload.toString()));
    }

    private void persistAssistantReply(AgentMessage parentMessage, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        store.createAssistantMessage(UUID.randomUUID().toString(), parentMessage.conversationUid(), parentMessage.messageUid(), content);
    }

    private List<MessageFileLinkDto> buildMessageFileLinks(AgentMessage message) {
        if (!"assistant".equals(message.role()) || message.parentMessageUid() == null || message.parentMessageUid().isBlank()) {
            return List.of();
        }
        Map<String, MessageFileLinkDto> files = new LinkedHashMap<>();
        for (AgentEvent event : store.listEventsByMessage(message.parentMessageUid())) {
            if (event.eventType() != AgentEventType.STEP_FINISHED || event.payload() == null || !event.payload().path("success").asBoolean(false)) {
                continue;
            }
            String path = event.payload().path("artifacts").path("path").asString("");
            if (path.isBlank()) {
                continue;
            }
            Path filePath = Path.of(path).toAbsolutePath().normalize();
            if (!Files.isRegularFile(filePath)) {
                continue;
            }
            String key = filePath.toString();
            files.putIfAbsent(key, new MessageFileLinkDto(filePath.getFileName().toString(), key));
        }
        return List.copyOf(files.values());
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

    /**
     * 将 message 的步骤与事件流合并为“运行视图”。
     *
     * <p>该方法属于展示层拼装，不参与执行决策。
     */
    private ConversationMessageRunDto toMessageRunResponse(AgentMessage message) {
        return runViewAssembler.toMessageRunResponse(
                message,
                store.listSteps(message.messageUid()),
                store.listEventsByMessage(message.messageUid())
        );
    }

    private String normalizeRunStatus(String currentStatus, List<ConversationRunStepDto> steps) {
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

    private String buildRunSummary(String status, int completedSteps, int totalSteps) {
        return switch (nullToEmpty(status)) {
            case "completed" -> i18n("agent.run.summary.completed", completedSteps, totalSteps);
            case "failed" -> i18n("agent.run.summary.failed", completedSteps, totalSteps);
            case "canceled" -> i18n("agent.run.summary.canceled", completedSteps, totalSteps);
            case "waiting_approval" -> i18n("agent.run.summary.waitingApproval", completedSteps, totalSteps);
            case "running" -> i18n("agent.run.summary.running", completedSteps, totalSteps);
            default -> i18n("agent.run.summary.planned", totalSteps);
        };
    }

    private String i18n(String code, Object... args) {
        return localizedMessages.get(code, args);
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
        if (total == 0 && (input > 0 || output > 0)) {
            total = input + output;
        }

        if (input == 0 && output == 0 && total == 0 && modelName.isBlank() && provider.isBlank()) {
            return;
        }

        store.accumulateMessageTokenUsage(
                message.messageUid(),
                provider,
                modelName,
                input,
                output,
                total
        );

        ObjectNode payload = basePayload("round token usage recorded");
        payload.put("roundIndex", roundIndex);
        payload.put("provider", provider);
        payload.put("modelName", modelName);
        payload.put("inputTokens", input);
        payload.put("outputTokens", output);
        payload.put("totalTokens", total);
        publishEvent(AgentEventType.ROUND_TOKEN_USAGE, message.conversationUid(), message.messageUid(), null, payload);

        log.info("[Agent] round token usage messageUid={} round={} provider={} model={} input={} output={} total={}",
                message.messageUid(), roundIndex, provider, modelName, input, output, total);
    }

    private String toolCallId(PlanStep step) {
        String toolCallId = step.toolArgs().path("_toolCallId").asString("");
        return toolCallId.isBlank() ? step.stepUid() : toolCallId;
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

        static RunStepAccumulator fromStep(PlanStep step, String displayTitle, String displayDetails) {
            RunStepAccumulator accumulator = new RunStepAccumulator(step.stepUid());
            accumulator.roundIndex = step.roundIndex();
            accumulator.stepIndex = step.stepIndex();
            accumulator.toolName = step.toolName() == null ? "" : step.toolName();
            accumulator.toolArgs = copyJson(step.toolArgs());
            accumulator.displayTitle = displayTitle;
            accumulator.displaySummary = "";
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
            builder.append("reviewMessage=").append(reviewMessage).append('\n');
        }
        return builder.toString().trim();
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

    public AgentConversation getConversation(String conversationUid) {
        return store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
    }

    // ===== 运行上下文解析已迁移至 ExecutionScopeResolver =====

    private boolean isCanceled(String messageUid, MessageStatus status) {
        return status == MessageStatus.CANCELED || cancellationRegistry.isCanceled(messageUid);
    }

    private String normalizeApprovalMode(String approvalMode) {
        String normalized = approvalMode == null ? "" : approvalMode.trim().toLowerCase(Locale.ROOT);
        return APPROVAL_MODE_FULL_ACCESS.equals(normalized) ? APPROVAL_MODE_FULL_ACCESS : APPROVAL_MODE_DEFAULT;
    }

    private ToolPolicyDecisionResult applyApprovalModeOverride(AgentMessage message, ToolPolicyDecisionResult decision) {
        if (decision == null || message == null) {
            return decision;
        }
        String approvalMode = messageExecutionOrchestrator.approvalMode(message.messageUid(), APPROVAL_MODE_DEFAULT);
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

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String buildConversationTitle(String message) {
        if (message == null || message.isBlank()) {
            return "未命名对话";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 24) {
            return normalized;
        }
        return normalized.substring(0, 24) + "...";
    }

    private String normalizeAgentGroupUid(String agentGroupUid) {
        return agentGroupUid == null || agentGroupUid.isBlank() ? "" : agentGroupUid.trim();
    }

    private String normalizeAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? DEFAULT_AGENT_UID : agentUid.trim();
    }

    private String normalizeOptionalAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? "" : agentUid.trim();
    }


    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        int limit = 2000;
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
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

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
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
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private record StepExecutionOutcome(boolean canceled, ToolResult toolResult, String memoryText, ChatMessage injectedMemoryMessage) {
        static StepExecutionOutcome success(ToolResult result, String memoryText) {
            return new StepExecutionOutcome(false, result, memoryText, null);
        }

        static StepExecutionOutcome failure(ToolResult result, String memoryText) {
            return new StepExecutionOutcome(false, result, memoryText, null);
        }

        static StepExecutionOutcome cancelled() {
            return new StepExecutionOutcome(true,
                    ToolResult.failure("CANCELLED", "message canceled", JsonNodeFactory.instance.objectNode()),
                    "errorCode=CANCELLED\nerrorMessage=message canceled",
                    null);
        }
    }

}
