package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.util.UuidUtil;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionRuntimeStateStore;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.entity.AgentCronJobExecutionEntity;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobExecutionRepository;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class CronJobExecutionService {

    private static final DateTimeFormatter REPORT_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final AgentCronJobRepository agentCronJobRepository;
    private final AgentCronJobExecutionRepository agentCronJobExecutionRepository;
    private final AgentApplicationService agentApplicationService;
    private final CronNotificationFanoutService cronNotificationFanoutService;
    private final CronJobSchedulerService cronJobSchedulerService;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentStore store;
    private final int defaultApprovalTimeoutSeconds;
    private final int runningStaleTimeoutSeconds;
    private final ExecutionRuntimeStateStore executionRuntimeStateStore;

    public CronJobExecutionService(AgentCronJobRepository agentCronJobRepository,
                                   AgentCronJobExecutionRepository agentCronJobExecutionRepository,
                                   AgentApplicationService agentApplicationService,
                                   CronNotificationFanoutService cronNotificationFanoutService,
                                   CronJobSchedulerService cronJobSchedulerService,
                                   AgentDefinitionRepository agentDefinitionRepository,
                                   AgentStore store,
                                   @Value("${nomoclaw.cron.approval-timeout-seconds:1800}") int defaultApprovalTimeoutSeconds,
                                   @Value("${nomoclaw.cron.running-stale-timeout-seconds:60}") int runningStaleTimeoutSeconds,
                                   ExecutionRuntimeStateStore executionRuntimeStateStore) {
        this.agentCronJobRepository = agentCronJobRepository;
        this.agentCronJobExecutionRepository = agentCronJobExecutionRepository;
        this.agentApplicationService = agentApplicationService;
        this.cronNotificationFanoutService = cronNotificationFanoutService;
        this.cronJobSchedulerService = cronJobSchedulerService;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.store = store;
        this.defaultApprovalTimeoutSeconds = Math.max(defaultApprovalTimeoutSeconds, 1);
        this.runningStaleTimeoutSeconds = Math.max(runningStaleTimeoutSeconds, 1);
        this.executionRuntimeStateStore = executionRuntimeStateStore;
    }

    public void executeJob(String jobUid) {
        AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
        if (job == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        CurrentExecutionContext currentExecution = resolveOrCreateCurrentExecution(job, now);
        String executionUid = currentExecution.executionUid();
        String conversationUid = currentExecution.conversationUid();
        String messageUid = currentExecution.messageUid();

        if (conversationUid == null || conversationUid.isBlank() || messageUid == null || messageUid.isBlank()) {
            conversationUid = createCronConversation(job);
            RuntimeModelSelection runtimeModel = resolveRuntimeModel(job);
            messageUid = submitCronMessage(conversationUid, job.getTaskContent(), runtimeModel);
            markCurrentExecution(job, executionUid, conversationUid, messageUid, now);
        }

        touchExecutionActiveState(executionUid, "RUNNING", now);
    }

    public String initializeCurrentExecution(String jobUid, LocalDateTime now) {
        AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
        if (job == null) {
            throw new IllegalArgumentException("cron job not found: " + jobUid);
        }
        String executionUid = UuidUtil.newUuid();
        String conversationUid = createCronConversation(job);
        RuntimeModelSelection runtimeModel = resolveRuntimeModel(job);
        String messageUid = submitCronMessage(conversationUid, job.getTaskContent(), runtimeModel);
        createRunningExecution(job, executionUid, conversationUid, messageUid, now);
        updateJobConversationPointers(job.getJobUid(), conversationUid, messageUid, now);
        return executionUid;
    }

    private String createCronConversation(AgentCronJobEntity job) {
        String conversationUid = agentApplicationService.createConversation("", job.getAgentUid(), "cron");
        String title = job == null || job.getTitle() == null ? "" : job.getTitle().trim();
        if (!title.isBlank()) {
            agentApplicationService.updateConversationTitle(conversationUid, title);
        }
        return conversationUid;
    }

    public void resumeActiveExecutions(int limit) {
        List<AgentCronJobExecutionEntity> activeExecutions = agentCronJobExecutionRepository.listActiveForResume(limit);
        for (AgentCronJobExecutionEntity execution : activeExecutions) {
            try {
                processExecutionState(execution);
            } catch (Exception ex) {
                log.warn("[Cron] resume execution failed executionUid={} err={}", execution.getExecutionUid(), ex.toString());
            }
        }
    }

    public void signalResumeByMessageUid(String messageUid) {
        agentCronJobExecutionRepository.markResumeRequestedByMessageUid(messageUid);
    }

    private void processExecutionState(AgentCronJobExecutionEntity execution) {
        String executionUid = blankToNull(execution.getExecutionUid());
        String messageUid = blankToNull(execution.getMessageUid());
        if (executionUid == null || messageUid == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentMessage message = agentApplicationService.getMessage(messageUid);
        if (message == null || message.status() == null) {
            failExecutionIfStale(execution, now, "任务状态丢失（服务重启或进程中断），执行已终止。");
            return;
        }

        MessageStatus messageStatus = message.status();
        if (messageStatus == MessageStatus.WAITING_APPROVAL) {
            handleWaitingApproval(execution, now);
            return;
        }
        if (messageStatus == MessageStatus.RUNNING
                || messageStatus == MessageStatus.CREATED
                || messageStatus == MessageStatus.PLANNED
                || messageStatus == MessageStatus.REPLANNING) {
            boolean runtimeRunning = executionRuntimeStateStore.isRunning(messageUid);
            boolean executionStale = isExecutionStateStale(execution, now);
            boolean messageStale = isMessageStateStale(message, now);
            if (executionStale && (!runtimeRunning || messageStale)) {
                failExecutionIfStale(execution, now, "任务在服务重启后未恢复运行，执行已终止。");
                return;
            }
            touchExecutionActiveState(executionUid, "RUNNING", now);
            return;
        }
        if (messageStatus == MessageStatus.COMPLETED
                || messageStatus == MessageStatus.FAILED
                || messageStatus == MessageStatus.CANCELED) {
            completeExecution(execution, message, now);
        }
    }

    private void handleWaitingApproval(AgentCronJobExecutionEntity execution, LocalDateTime now) {
        String executionUid = blankToNull(execution.getExecutionUid());
        if (executionUid == null) {
            return;
        }
        LocalDateTime waitStartedAt = execution.getApprovalWaitStartedTime();
        int timeoutSeconds = execution.getApprovalTimeoutSeconds() == null || execution.getApprovalTimeoutSeconds() <= 0
                ? defaultApprovalTimeoutSeconds
                : execution.getApprovalTimeoutSeconds();

        if (waitStartedAt == null) {
            agentCronJobExecutionRepository.update(new LambdaUpdateWrapper<AgentCronJobExecutionEntity>()
                    .eq(AgentCronJobExecutionEntity::getExecutionUid, executionUid)
                    .set(AgentCronJobExecutionEntity::getStatus, "WAITING_APPROVAL")
                    .set(AgentCronJobExecutionEntity::getApprovalWaitStartedTime, now)
                    .set(AgentCronJobExecutionEntity::getApprovalTimeoutSeconds, timeoutSeconds)
                    .set(AgentCronJobExecutionEntity::getResumeRequested, 0)
                    .set(AgentCronJobExecutionEntity::getUpdatedTime, now));
            return;
        }

        if (waitStartedAt.plusSeconds(timeoutSeconds).isAfter(now)) {
            touchExecutionActiveState(executionUid, "WAITING_APPROVAL", now);
            return;
        }

        AgentCronJobEntity job = agentCronJobRepository.findByJobUid(execution.getJobUid());
        if (job == null) {
            return;
        }
        String timeoutSummary = summarize("等待审批超时，执行已终止。请在会话中手动继续或重试任务。");
        updateJobResult(
                job,
                now,
                timeoutSummary,
                null,
                "TIMED_OUT_APPROVAL",
                executionUid,
                blankToNull(execution.getConversationUid()),
                blankToNull(execution.getMessageUid())
        );
    }

    private void completeExecution(AgentCronJobExecutionEntity execution, AgentMessage message, LocalDateTime now) {
        String executionUid = blankToNull(execution.getExecutionUid());
        if (executionUid == null || execution.getFinishedTime() != null) {
            return;
        }
        AgentCronJobEntity job = agentCronJobRepository.findByJobUid(execution.getJobUid());
        if (job == null) {
            return;
        }

        String conversationUid = blankToNull(execution.getConversationUid());
        String messageUid = blankToNull(execution.getMessageUid());
        String executionStatus = toExecutionStatus(message.status());
        String finalContent = loadFinalAnswer(conversationUid, messageUid, message);
        String summary = summarize(finalContent);
        Instant executedAt = execution.getStartedTime() == null
                ? Instant.now()
                : execution.getStartedTime().atZone(ZoneId.systemDefault()).toInstant();
        Path reportPath = null;
        try {
            reportPath = writeReport(job, finalContent, executedAt, executionStatus);
        } catch (Exception ex) {
            log.warn("[Cron] write report failed executionUid={} err={}", executionUid, ex.toString());
        }
        notifyCompletion(job, finalContent, reportPath, executedAt, executionStatus);
        updateJobResult(job, now, summary, reportPath, executionStatus, executionUid, conversationUid, messageUid);
    }

    private String toExecutionStatus(MessageStatus status) {
        if (status == null) {
            return "FAILED";
        }
        return switch (status) {
            case COMPLETED -> "COMPLETED";
            case CANCELED -> "CANCELED";
            case FAILED -> "FAILED";
            case WAITING_APPROVAL -> "WAITING_APPROVAL";
            default -> "RUNNING";
        };
    }

    private CurrentExecutionContext resolveOrCreateCurrentExecution(AgentCronJobEntity job, LocalDateTime now) {
        AgentCronJobExecutionEntity running = agentCronJobExecutionRepository.findLatestRunningByJobUid(job.getJobUid());
        if (running != null) {
            return new CurrentExecutionContext(
                    running.getExecutionUid(),
                    blankToNull(running.getConversationUid()),
                    blankToNull(running.getMessageUid())
            );
        }
        String executionUid = UuidUtil.newUuid();
        createRunningExecution(job, executionUid, null, null, now);
        return new CurrentExecutionContext(executionUid, null, null);
    }

    private void createRunningExecution(AgentCronJobEntity job,
                                        String executionUid,
                                        String conversationUid,
                                        String messageUid,
                                        LocalDateTime now) {
        AgentCronJobExecutionEntity entity = new AgentCronJobExecutionEntity();
        entity.setExecutionUid(executionUid == null ? "" : executionUid);
        entity.setJobUid(job.getJobUid());
        entity.setAgentUid(job.getAgentUid());
        entity.setConversationUid(conversationUid == null ? "" : conversationUid);
        entity.setMessageUid(messageUid == null ? "" : messageUid);
        entity.setStatus("RUNNING");
        entity.setSummary("");
        entity.setReportPath("");
        entity.setApprovalWaitStartedTime(null);
        entity.setApprovalTimeoutSeconds(defaultApprovalTimeoutSeconds);
        entity.setResumeRequested(0);
        entity.setReadFlag(0);
        entity.setStartedTime(now);
        entity.setFinishedTime(null);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);
        agentCronJobExecutionRepository.save(entity);
    }

    private void markCurrentExecution(AgentCronJobEntity job,
                                      String executionUid,
                                      String conversationUid,
                                      String messageUid,
                                      LocalDateTime now) {
        String normalizedConversationUid = conversationUid == null ? "" : conversationUid;
        String normalizedMessageUid = messageUid == null ? "" : messageUid;
        agentCronJobExecutionRepository.update(new LambdaUpdateWrapper<AgentCronJobExecutionEntity>()
                .eq(AgentCronJobExecutionEntity::getExecutionUid, executionUid)
                .set(AgentCronJobExecutionEntity::getConversationUid, normalizedConversationUid)
                .set(AgentCronJobExecutionEntity::getMessageUid, normalizedMessageUid)
                .set(AgentCronJobExecutionEntity::getStatus, "RUNNING")
                .set(AgentCronJobExecutionEntity::getApprovalWaitStartedTime, null)
                .set(AgentCronJobExecutionEntity::getApprovalTimeoutSeconds, defaultApprovalTimeoutSeconds)
                .set(AgentCronJobExecutionEntity::getResumeRequested, 0)
                .set(AgentCronJobExecutionEntity::getUpdatedTime, now));
        updateJobConversationPointers(job.getJobUid(), normalizedConversationUid, normalizedMessageUid, now);
    }

    private void touchExecutionActiveState(String executionUid, String status, LocalDateTime now) {
        if (executionUid == null || executionUid.isBlank()) {
            return;
        }
        LambdaUpdateWrapper<AgentCronJobExecutionEntity> wrapper = new LambdaUpdateWrapper<AgentCronJobExecutionEntity>()
                .eq(AgentCronJobExecutionEntity::getExecutionUid, executionUid)
                .set(AgentCronJobExecutionEntity::getStatus, status)
                .set(AgentCronJobExecutionEntity::getResumeRequested, 0)
                .set(AgentCronJobExecutionEntity::getUpdatedTime, now);
        if (!"WAITING_APPROVAL".equals(status)) {
            wrapper.set(AgentCronJobExecutionEntity::getApprovalWaitStartedTime, null);
        }
        agentCronJobExecutionRepository.update(wrapper);
    }

    private void updateJobConversationPointers(String jobUid, String conversationUid, String messageUid, LocalDateTime now) {
        agentCronJobRepository.update(new LambdaUpdateWrapper<AgentCronJobEntity>()
                .eq(AgentCronJobEntity::getJobUid, jobUid)
                .set(AgentCronJobEntity::getConversationUid, conversationUid)
                .set(AgentCronJobEntity::getMessageUid, messageUid)
                .set(AgentCronJobEntity::getUpdatedTime, now));
    }

    private void failExecutionIfStale(AgentCronJobExecutionEntity execution, LocalDateTime now, String summary) {
        if (!isExecutionStateStale(execution, now)) {
            return;
        }
        AgentCronJobEntity job = agentCronJobRepository.findByJobUid(execution.getJobUid());
        if (job == null) {
            return;
        }
        updateJobResult(
                job,
                now,
                summarize(summary),
                null,
                "FAILED",
                blankToNull(execution.getExecutionUid()),
                blankToNull(execution.getConversationUid()),
                blankToNull(execution.getMessageUid())
        );
    }

    private boolean isExecutionStateStale(AgentCronJobExecutionEntity execution, LocalDateTime now) {
        LocalDateTime referenceTime = execution.getUpdatedTime() == null ? execution.getStartedTime() : execution.getUpdatedTime();
        if (referenceTime == null) {
            return true;
        }
        return referenceTime.plusSeconds(runningStaleTimeoutSeconds).isBefore(now);
    }

    private boolean isMessageStateStale(AgentMessage message, LocalDateTime now) {
        if (message == null || message.updatedAt() == null) {
            return true;
        }
        LocalDateTime messageUpdatedTime = LocalDateTime.ofInstant(message.updatedAt(), ZoneId.systemDefault());
        return messageUpdatedTime.plusSeconds(runningStaleTimeoutSeconds).isBefore(now);
    }

    private String loadFinalAnswer(String conversationUid, String messageUid, AgentMessage completedMessage) {
        if (conversationUid != null && !conversationUid.isBlank() && messageUid != null && !messageUid.isBlank()) {
            List<ConversationMessageDto> messages = agentApplicationService.listMessages(conversationUid);
            return messages.stream()
                    .filter(message -> "assistant".equals(message.role()) && messageUid.equals(message.parentMessageUid()))
                    .reduce((first, second) -> second)
                    .map(ConversationMessageDto::content)
                    .filter(text -> text != null && !text.isBlank())
                    .orElseGet(() -> completedMessage.status().name() + ": " + (completedMessage.content() == null ? "" : completedMessage.content()));
        }
        return completedMessage.status().name() + ": " + (completedMessage.content() == null ? "" : completedMessage.content());
    }

    private Path writeReport(AgentCronJobEntity job, String content, Instant executedAt, String status) throws Exception {
        AgentDefinitionEntity agent = resolveAgent(job);
        String agentName = resolveAgentName(agent);
        Path reportDir = AgentWorkspaceConfig.resolve(agentName, agent == null ? "" : agent.getWorkspace())
                .reportDir()
                .resolve("cron")
                .resolve(job.getJobUid())
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(reportDir);
        Path reportPath = reportDir.resolve(REPORT_NAME_FORMATTER.format(LocalDateTime.ofInstant(executedAt, ZoneId.systemDefault())) + ".md");
        String body = """
                # 定时任务执行结果

                - 任务ID: %s
                - 标题: %s
                - Agent: %s
                - 执行时间: %s
                - 状态: %s

                ## 原始任务

                %s

                ## 输出结果

                %s
                """.formatted(
                job.getJobUid(),
                job.getTitle() == null ? "" : job.getTitle(),
                agentName,
                executedAt,
                status,
                job.getTaskContent(),
                content == null ? "" : content
        );
        Files.writeString(reportPath, body, StandardCharsets.UTF_8);
        return reportPath;
    }

    private void notifyCompletion(AgentCronJobEntity job, String content, Path reportPath, Instant executedAt, String status) {
        cronNotificationFanoutService.fanout(job, content, reportPath, executedAt, status);
    }

    private void updateJobResult(AgentCronJobEntity job,
                                 LocalDateTime now,
                                 String summary,
                                 Path reportPath,
                                 String executionStatus,
                                 String executionUid,
                                 String conversationUid,
                                 String messageUid) {
        String normalizedConversationUid = conversationUid == null ? "" : conversationUid;
        String normalizedMessageUid = messageUid == null ? "" : messageUid;
        String normalizedSummary = summary == null ? "" : summary;
        String normalizedReportPath = reportPath == null ? "" : reportPath.toString();
        agentCronJobExecutionRepository.update(new LambdaUpdateWrapper<AgentCronJobExecutionEntity>()
                .eq(AgentCronJobExecutionEntity::getExecutionUid, executionUid)
                .set(AgentCronJobExecutionEntity::getConversationUid, normalizedConversationUid)
                .set(AgentCronJobExecutionEntity::getMessageUid, normalizedMessageUid)
                .set(AgentCronJobExecutionEntity::getStatus, executionStatus)
                .set(AgentCronJobExecutionEntity::getSummary, normalizedSummary)
                .set(AgentCronJobExecutionEntity::getReportPath, normalizedReportPath)
                .set(AgentCronJobExecutionEntity::getFinishedTime, now)
                .set(AgentCronJobExecutionEntity::getApprovalWaitStartedTime, null)
                .set(AgentCronJobExecutionEntity::getResumeRequested, 0)
                .set(AgentCronJobExecutionEntity::getUpdatedTime, now));
        LocalDateTime nextRunTime = cronJobSchedulerService.nextRunTime(job.getJobUid(), job.getTimezone());
        String nextStatus = job.getStatus();
        if (nextRunTime == null && "ACTIVE".equalsIgnoreCase(job.getStatus())) {
            nextStatus = "PAUSED";
        }
        agentCronJobRepository.update(new LambdaUpdateWrapper<AgentCronJobEntity>()
                .eq(AgentCronJobEntity::getJobUid, job.getJobUid())
                .set(AgentCronJobEntity::getLastRunTime, now)
                .set(AgentCronJobEntity::getNextRunTime, nextRunTime)
                .set(AgentCronJobEntity::getStatus, nextStatus)
                .set(AgentCronJobEntity::getLastResult, normalizedSummary)
                .set(AgentCronJobEntity::getConversationUid, normalizedConversationUid)
                .set(AgentCronJobEntity::getMessageUid, normalizedMessageUid)
                .set(AgentCronJobEntity::getUpdatedTime, now));
        syncMessageTerminalStatus(normalizedMessageUid, executionStatus);
    }

    private void syncMessageTerminalStatus(String messageUid, String executionStatus) {
        if (messageUid == null || messageUid.isBlank() || executionStatus == null || executionStatus.isBlank()) {
            return;
        }
        MessageStatus messageStatus = switch (executionStatus) {
            case "COMPLETED" -> MessageStatus.COMPLETED;
            case "CANCELED" -> MessageStatus.CANCELED;
            case "FAILED", "TIMED_OUT_APPROVAL" -> MessageStatus.FAILED;
            default -> null;
        };
        if (messageStatus == null) {
            return;
        }
        store.updateMessageStatus(messageUid, messageStatus);
    }

    private AgentDefinitionEntity resolveAgent(AgentCronJobEntity job) {
        if (job.getAgentUid() == null || job.getAgentUid().isBlank()) {
            return null;
        }
        return agentDefinitionRepository.findActiveByUid(job.getAgentUid());
    }

    private String resolveAgentName(AgentDefinitionEntity agent) {
        if (agent == null || agent.getAgentName() == null || agent.getAgentName().isBlank()) {
            return NomoClawPaths.DEFAULT_AGENT_NAME;
        }
        return agent.getAgentName();
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private RuntimeModelSelection resolveRuntimeModel(AgentCronJobEntity job) {
        if (job == null || job.getExtConfig() == null || job.getExtConfig().isBlank()) {
            return new RuntimeModelSelection("", "");
        }
        JsonNode extNode = JsonUtil.fromJsonQuietly(job.getExtConfig(), JsonNode.class).orElse(JsonNodeFactory.instance.objectNode());
        String modelProvider = extNode.path("runtimeModel").path("provider").asString("").trim();
        String modelName = extNode.path("runtimeModel").path("name").asString("").trim();
        return new RuntimeModelSelection(modelProvider, modelName);
    }

    private String submitCronMessage(String conversationUid, String taskContent, RuntimeModelSelection runtimeModel) {
        return agentApplicationService.submitMessage(
                conversationUid,
                taskContent,
                List.of(),
                runtimeModel.modelProvider(),
                runtimeModel.modelName(),
                "default",
                "cron",
                null
        );
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private record CurrentExecutionContext(String executionUid, String conversationUid, String messageUid) {
    }

    private record RuntimeModelSelection(String modelProvider, String modelName) {
    }
}
