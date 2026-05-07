package ai.nomoclaw.bot.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ai.nomoclaw.bot.application.dto.ConversationMessageDto;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CronJobExecutionService {

    private static final DateTimeFormatter REPORT_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final AgentCronJobRepository agentCronJobRepository;
    private final AgentApplicationService agentApplicationService;
    private final CronNotificationFanoutService cronNotificationFanoutService;
    private final CronJobSchedulerService cronJobSchedulerService;
    private final AgentDefinitionRepository agentDefinitionRepository;

    public CronJobExecutionService(AgentCronJobRepository agentCronJobRepository,
                                   AgentApplicationService agentApplicationService,
                                   CronNotificationFanoutService cronNotificationFanoutService,
                                   CronJobSchedulerService cronJobSchedulerService,
                                   AgentDefinitionRepository agentDefinitionRepository) {
        this.agentCronJobRepository = agentCronJobRepository;
        this.agentApplicationService = agentApplicationService;
        this.cronNotificationFanoutService = cronNotificationFanoutService;
        this.cronJobSchedulerService = cronJobSchedulerService;
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    public void executeJob(String jobUid) {
        AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
        if (job == null) {
            return;
        }

        Instant executedAt = Instant.now();
        LocalDateTime now = LocalDateTime.now();
        CurrentExecutionContext currentExecution = resolveOrCreateCurrentExecution(job, now);
        String executionUid = currentExecution.executionUid();
        String conversationUid = currentExecution.conversationUid();
        String messageUid = currentExecution.messageUid();
        try {
            if (conversationUid == null || conversationUid.isBlank() || messageUid == null || messageUid.isBlank()) {
                // Mark scheduled runs with channel=cron so planner/executor can apply cron-specific safeguards.
                conversationUid = agentApplicationService.createConversation("", job.getAgentUid(), "cron");
                messageUid = agentApplicationService.submitMessage(conversationUid, job.getTaskContent(), "cron");
                markCurrentExecution(job, executionUid, conversationUid, messageUid, now);
            }
            AgentMessage completedMessage = waitForCompletion(messageUid);
            String finalContent = loadFinalAnswer(conversationUid, messageUid, completedMessage);
            Path reportPath = writeReport(job, finalContent, executedAt, completedMessage.status().name());
            notifyCompletion(job, finalContent, reportPath, executedAt, completedMessage.status().name());
            updateJobResult(job, now, summarize(finalContent), reportPath, "COMPLETED", executionUid, conversationUid, messageUid);
        } catch (Exception ex) {
            Path reportPath = writeFailureReportSafely(job, ex, executedAt);
            String failureSummary = summarize(ex.getMessage() == null ? "cron execution failed" : ex.getMessage());
            notifyCompletion(job, failureSummary, reportPath, executedAt, "FAILED");
            updateJobResult(job, now, failureSummary, reportPath, "FAILED", executionUid, conversationUid, messageUid);
            log.error("[Quartz] cron job execution failed jobUid={}", jobUid, ex);
        }
    }

    public String initializeCurrentExecution(String jobUid, LocalDateTime now) {
        AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
        if (job == null) {
            throw new IllegalArgumentException("cron job not found: " + jobUid);
        }
        String executionUid = UUID.randomUUID().toString();
        // Manual trigger: create conversation/message immediately so UI can open execution chat right away.
        String conversationUid = agentApplicationService.createConversation("", job.getAgentUid(), "cron");
        String messageUid = agentApplicationService.submitMessage(conversationUid, job.getTaskContent(), "cron");
        markCurrentExecution(job, executionUid, conversationUid, messageUid, now);
        return executionUid;
    }

    private CurrentExecutionContext resolveOrCreateCurrentExecution(AgentCronJobEntity job, LocalDateTime now) {
        JsonNode extNode = job.getExtConfig() == null || job.getExtConfig().isBlank()
                ? JsonNodeFactory.instance.objectNode()
                : JsonUtil.fromJsonQuietly(job.getExtConfig(), JsonNode.class).orElse(JsonNodeFactory.instance.objectNode());
        JsonNode currentExecution = extNode.path("currentExecution");
        String existingExecutionUid = currentExecution.path("executionUid").asText("");
        String existingConversationUid = currentExecution.path("conversationUid").asText("");
        String existingMessageUid = currentExecution.path("messageUid").asText("");
        if (!existingExecutionUid.isBlank()) {
            return new CurrentExecutionContext(
                    existingExecutionUid,
                    existingConversationUid.isBlank() ? null : existingConversationUid,
                    existingMessageUid.isBlank() ? null : existingMessageUid
            );
        }
        String generatedExecutionUid = UUID.randomUUID().toString();
        markCurrentExecution(job, generatedExecutionUid, null, null, now);
        return new CurrentExecutionContext(generatedExecutionUid, null, null);
    }

    private void markCurrentExecution(AgentCronJobEntity job,
                                      String executionUid,
                                      String conversationUid,
                                      String messageUid,
                                      LocalDateTime now) {
        String normalizedConversationUid = conversationUid == null ? "" : conversationUid;
        String normalizedMessageUid = messageUid == null ? "" : messageUid;
        ObjectNode extConfig = job.getExtConfig() == null || job.getExtConfig().isBlank()
                ? JsonNodeFactory.instance.objectNode()
                : (ObjectNode) JsonUtil.fromJsonQuietly(job.getExtConfig(), tools.jackson.databind.JsonNode.class)
                .orElse(JsonNodeFactory.instance.objectNode());
        ObjectNode currentExecution = JsonNodeFactory.instance.objectNode();
        currentExecution.put("executionUid", executionUid == null ? "" : executionUid);
        currentExecution.put("conversationUid", normalizedConversationUid);
        currentExecution.put("messageUid", normalizedMessageUid);
        currentExecution.put("status", "RUNNING");
        currentExecution.put("startedTime", now.toString());
        extConfig.set("currentExecution", currentExecution);
        agentCronJobRepository.update(new LambdaUpdateWrapper<AgentCronJobEntity>()
                .eq(AgentCronJobEntity::getJobUid, job.getJobUid())
                .set(AgentCronJobEntity::getConversationUid, normalizedConversationUid)
                .set(AgentCronJobEntity::getMessageUid, normalizedMessageUid)
                .set(AgentCronJobEntity::getExtConfig, JsonUtil.toJson(extConfig))
                .set(AgentCronJobEntity::getUpdatedTime, now));
    }

    private AgentMessage waitForCompletion(String messageUid) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10 * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            AgentMessage message = agentApplicationService.getMessage(messageUid);
            switch (message.status()) {
                case COMPLETED, FAILED, CANCELED -> {
                    return message;
                }
                default -> Thread.sleep(1000L);
            }
        }
        throw new IllegalStateException("cron execution timed out");
    }

    private String loadFinalAnswer(String conversationUid, String messageUid, AgentMessage completedMessage) {
        List<ConversationMessageDto> messages = agentApplicationService.listMessages(conversationUid);
        return messages.stream()
                .filter(message -> "assistant".equals(message.role()) && messageUid.equals(message.parentMessageUid()))
                .reduce((first, second) -> second)
                .map(ConversationMessageDto::content)
                .filter(text -> text != null && !text.isBlank())
                .orElseGet(() -> completedMessage.status().name() + ": " + (completedMessage.content() == null ? "" : completedMessage.content()));
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

    private Path writeFailureReportSafely(AgentCronJobEntity job, Exception ex, Instant executedAt) {
        try {
            return writeReport(job, ex.getMessage() == null ? "执行失败" : ex.getMessage(), executedAt, "FAILED");
        } catch (Exception ignored) {
            return null;
        }
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
        ObjectNode extConfig = job.getExtConfig() == null || job.getExtConfig().isBlank()
                ? JsonNodeFactory.instance.objectNode()
                : (ObjectNode) JsonUtil.fromJsonQuietly(job.getExtConfig(), tools.jackson.databind.JsonNode.class)
                .orElse(JsonNodeFactory.instance.objectNode());
        extConfig.put("lastReportPath", reportPath == null ? "" : reportPath.toString());
        extConfig.remove("currentExecution");
        appendExecutionResult(extConfig, now, summary, reportPath, executionStatus, executionUid, normalizedConversationUid, normalizedMessageUid);
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
                .set(AgentCronJobEntity::getLastResult, summary == null ? "" : summary)
                .set(AgentCronJobEntity::getConversationUid, normalizedConversationUid)
                .set(AgentCronJobEntity::getMessageUid, normalizedMessageUid)
                .set(AgentCronJobEntity::getExtConfig, JsonUtil.toJson(extConfig))
                .set(AgentCronJobEntity::getUpdatedTime, now));
    }

    private void appendExecutionResult(ObjectNode extConfig,
                                       LocalDateTime executedTime,
                                       String summary,
                                       Path reportPath,
                                       String status,
                                       String executionUid,
                                       String conversationUid,
                                       String messageUid) {
        ArrayNode existing = extConfig.path("executionResults") instanceof ArrayNode arrayNode
                ? (ArrayNode) arrayNode.deepCopy()
                : JsonNodeFactory.instance.arrayNode();
        ArrayNode next = JsonNodeFactory.instance.arrayNode();
        ObjectNode current = JsonNodeFactory.instance.objectNode();
        current.put("executionUid", executionUid == null ? "" : executionUid);
        current.put("conversationUid", conversationUid == null ? "" : conversationUid);
        current.put("messageUid", messageUid == null ? "" : messageUid);
        current.put("executedTime", executedTime.toString());
        current.put("status", status == null ? "" : status);
        current.put("summary", summary == null ? "" : summary);
        current.put("reportPath", reportPath == null ? "" : reportPath.toString());
        current.put("read", false);
        next.add(current);
        for (JsonNode item : existing) {
            if (next.size() >= 20) {
                break;
            }
            next.add(item);
        }
        extConfig.set("executionResults", next);
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

    private record CurrentExecutionContext(String executionUid, String conversationUid, String messageUid) {
    }
}
