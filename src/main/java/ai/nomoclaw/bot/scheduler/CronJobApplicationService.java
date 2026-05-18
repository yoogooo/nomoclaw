package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.scheduler.model.CreateCronJobParam;
import ai.nomoclaw.bot.scheduler.model.UpdateCronJobParam;
import ai.nomoclaw.bot.common.page.PageRequest;
import ai.nomoclaw.bot.common.page.PageResult;
import ai.nomoclaw.bot.common.page.PageResultMapper;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.channel.config.ChannelBotCredentialResolver;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.conversation.model.ConversationMessageRunDto;
import ai.nomoclaw.bot.orchestrator.ConversationAppService;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.scheduler.model.BatchDeleteCronJobsDto;
import ai.nomoclaw.bot.scheduler.model.CronExecutionDetailDto;
import ai.nomoclaw.bot.scheduler.model.CronJobDto;
import ai.nomoclaw.bot.scheduler.model.CronJobExecutionResultDto;
import ai.nomoclaw.bot.scheduler.model.CronJobReportDto;
import ai.nomoclaw.bot.scheduler.model.CronSubscriptionDto;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.entity.AgentCronJobExecutionEntity;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobExecutionRepository;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class CronJobApplicationService {
    private static final int RESULT_REPORT_PREVIEW_LIMIT = 16_000;
    private static final List<String> FINISHED_EXECUTION_STATUSES = List.of("COMPLETED", "FAILED", "CANCELED", "TIMED_OUT_APPROVAL");

    private final AgentCronJobRepository agentCronJobRepository;
    private final AgentCronJobExecutionRepository agentCronJobExecutionRepository;
    private final CronJobSchedulerService cronJobSchedulerService;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final CronSubscriptionRepository cronSubscriptionRepository;
    private final AgentChannelsProperties channelsProperties;
    private final ChannelBotCredentialResolver botCredentialResolver;
    private final ConversationAppService conversationAppService;
    private final CronJobExecutionService cronJobExecutionService;
    private final AgentApplicationService agentApplicationService;

    public CronJobApplicationService(AgentCronJobRepository agentCronJobRepository,
                                     AgentCronJobExecutionRepository agentCronJobExecutionRepository,
                                     CronJobSchedulerService cronJobSchedulerService,
                                     AgentDefinitionRepository agentDefinitionRepository,
                                     CronSubscriptionRepository cronSubscriptionRepository,
                                     AgentChannelsProperties channelsProperties,
                                     ChannelBotCredentialResolver botCredentialResolver,
                                     ConversationAppService conversationAppService,
                                     CronJobExecutionService cronJobExecutionService,
                                     AgentApplicationService agentApplicationService) {
        this.agentCronJobRepository = agentCronJobRepository;
        this.agentCronJobExecutionRepository = agentCronJobExecutionRepository;
        this.cronJobSchedulerService = cronJobSchedulerService;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.cronSubscriptionRepository = cronSubscriptionRepository;
        this.channelsProperties = channelsProperties;
        this.botCredentialResolver = botCredentialResolver;
        this.conversationAppService = conversationAppService;
        this.cronJobExecutionService = cronJobExecutionService;
        this.agentApplicationService = agentApplicationService;
    }

    public List<CronJobDto> listCronJobs() {
        List<AgentCronJobEntity> jobs = agentCronJobRepository.listAllJobs();
        Map<String, AgentDefinitionEntity> agentsByUid = loadAgentsByUid(jobs);
        return jobs.stream()
                .map(job -> toResponse(job, agentsByUid.get(job.getAgentUid())))
                .toList();
    }

    public CronJobDto getCronJob(String jobUid) {
        AgentCronJobEntity job = requireJob(jobUid);
        return toResponse(job, resolveAgent(job.getAgentUid()));
    }

    public CronJobDto createCronJob(CreateCronJobParam request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        String agentUid = request.agentUid() == null ? "" : request.agentUid().trim();
        if (agentUid.isBlank()) {
            throw new IllegalArgumentException("agentUid is required");
        }
        AgentDefinitionEntity agent = agentDefinitionRepository.findActiveByUid(agentUid);
        if (agent == null) {
            throw new IllegalArgumentException("agent not found: " + agentUid);
        }

        if (request.taskContent() == null || request.taskContent().isBlank()) {
            throw new IllegalArgumentException("taskContent is required");
        }
        if (request.expression() == null || request.expression().isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }

        String expression = normalizeCron(request.expression());
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? "Asia/Shanghai"
                : ZoneId.of(request.timezone().trim()).getId();
        String endAt = normalizeEndAt(request.endAt());
        String taskContent = request.taskContent().trim();
        String title = defaultCronJobTitle(request.title(), taskContent);
        String status = normalizeStatus(request.status(), "ACTIVE");
        LocalDateTime now = LocalDateTime.now();

        AgentCronJobEntity job = new AgentCronJobEntity();
        job.setJobUid(UUID.randomUUID().toString());
        job.setAgentUid(agent.getAgentUid());
        job.setConversationUid(null);
        job.setMessageUid(null);
        job.setTitle(title);
        job.setExpression(expression);
        job.setTimezone(timezone);
        job.setTaskContent(taskContent);
        job.setStatus(status);
        job.setLastRunTime(null);
        job.setNextRunTime(null);
        job.setLastResult("");
        job.setExtConfig("");
        job.setCreatedTime(now);
        job.setUpdatedTime(now);
        mergeExtConfig(job, expression, timezone, endAt);

        agentCronJobRepository.save(job);

        if ("ACTIVE".equals(status)) {
            LocalDateTime nextRunTime = cronJobSchedulerService.scheduleJob(job);
            job.setNextRunTime(nextRunTime);
            job.setUpdatedTime(LocalDateTime.now());
            agentCronJobRepository.updateById(job);
        }

        return toResponse(job, agent);
    }

    public CronJobDto updateCronJob(String jobUid, UpdateCronJobParam request) {
        if (request == null) {
            request = new UpdateCronJobParam(null, null, null, null, null, null, null);
        }
        AgentCronJobEntity job = requireJob(jobUid);
        String requestedAgentUid = request.agentUid() == null ? "" : request.agentUid().trim();
        String agentUid = requestedAgentUid.isBlank() ? job.getAgentUid() : requestedAgentUid;
        AgentDefinitionEntity agent = agentDefinitionRepository.findActiveByUid(agentUid);
        if (agent == null) {
            throw new IllegalArgumentException("agent not found: " + agentUid);
        }
        String title = request.title() == null || request.title().isBlank()
                ? defaultCronJobTitle(job.getTitle(), job.getTaskContent())
                : request.title().trim();
        String expression = request.expression() == null || request.expression().isBlank()
                ? job.getExpression()
                : normalizeCron(request.expression());
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? job.getTimezone()
                : ZoneId.of(request.timezone()).getId();
        String endAt = request.endAt() == null
                ? readScheduleEndAt(job.getExtConfig())
                : normalizeEndAt(request.endAt());
        String taskContent = request.taskContent() == null || request.taskContent().isBlank()
                ? job.getTaskContent()
                : request.taskContent().trim();
        String status = normalizeStatus(request.status(), job.getStatus());

        job.setAgentUid(agent.getAgentUid());
        job.setTitle(title);
        job.setExpression(expression);
        job.setTimezone(timezone);
        job.setTaskContent(taskContent);
        job.setStatus(status);
        mergeExtConfig(job, expression, timezone, endAt);

        LocalDateTime nextRunTime;
        if ("PAUSED".equals(status)) {
            nextRunTime = cronJobSchedulerService.rescheduleJob(job);
            cronJobSchedulerService.pauseJob(jobUid, timezone);
            nextRunTime = null;
        } else {
            nextRunTime = cronJobSchedulerService.rescheduleJob(job);
        }

        job.setNextRunTime(nextRunTime);
        job.setUpdatedTime(LocalDateTime.now());
        agentCronJobRepository.updateById(job);
        return toResponse(job, agent);
    }

    public CronJobDto pauseCronJob(String jobUid) {
        AgentCronJobEntity job = requireJob(jobUid);
        cronJobSchedulerService.pauseJob(jobUid, job.getTimezone());
        job.setStatus("PAUSED");
        job.setNextRunTime(null);
        job.setUpdatedTime(LocalDateTime.now());
        agentCronJobRepository.updateById(job);
        return toResponse(job, resolveAgent(job.getAgentUid()));
    }

    public CronJobDto resumeCronJob(String jobUid) {
        AgentCronJobEntity job = requireJob(jobUid);
        job.setStatus("ACTIVE");
        LocalDateTime nextRunTime = cronJobSchedulerService.resumeJob(job);
        job.setNextRunTime(nextRunTime);
        job.setUpdatedTime(LocalDateTime.now());
        agentCronJobRepository.updateById(job);
        return toResponse(job, resolveAgent(job.getAgentUid()));
    }

    public CronJobDto runCronJob(String jobUid) {
        cronJobExecutionService.initializeCurrentExecution(jobUid, LocalDateTime.now());
        cronJobSchedulerService.runNow(jobUid);
        AgentCronJobEntity refreshedJob = requireJob(jobUid);
        return toResponse(refreshedJob, resolveAgent(refreshedJob.getAgentUid()));
    }

    public void deleteCronJob(String jobUid) {
        AgentCronJobEntity job = requireJob(jobUid);
        cronJobSchedulerService.deleteJob(jobUid);
        cronSubscriptionRepository.deleteByJobUid(jobUid);
        agentCronJobRepository.removeById(job.getId());
    }

    public List<CronSubscriptionDto> listCronSubscriptions(String jobUid) {
        requireJob(jobUid);
        return cronSubscriptionRepository.listByJobUid(jobUid).stream()
                .map(item -> new CronSubscriptionDto(
                        item.subscriptionUid(),
                        item.jobUid(),
                        item.channel(),
                        item.target(),
                        item.botId(),
                        item.enabled(),
                        item.createdTime(),
                        item.updatedTime()
                ))
                .toList();
    }

    public List<CronSubscriptionDto> updateCronSubscriptions(String jobUid,
                                                             List<CronSubscriptionRepository.CronSubscriptionUpsert> subscriptions) {
        requireJob(jobUid);
        List<CronSubscriptionRepository.CronSubscriptionUpsert> normalized = normalizeSubscriptions(subscriptions);
        cronSubscriptionRepository.replace(jobUid, normalized);
        return listCronSubscriptions(jobUid);
    }

    public BatchDeleteCronJobsDto batchDeleteCronJobs(List<String> jobUids) {
        int requestedCount = jobUids == null ? 0 : jobUids.size();
        List<String> normalizedJobUids = normalizeBatchJobUids(jobUids);
        List<String> deletedJobUids = new java.util.ArrayList<>();
        List<BatchDeleteCronJobsDto.FailedItemDto> failedItems = new java.util.ArrayList<>();
        for (String jobUid : normalizedJobUids) {
            AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
            if (job == null) {
                failedItems.add(new BatchDeleteCronJobsDto.FailedItemDto(jobUid, "cron job not found"));
                continue;
            }
            try {
                cronJobSchedulerService.deleteJob(jobUid);
                cronSubscriptionRepository.deleteByJobUid(jobUid);
                agentCronJobRepository.removeById(job.getId());
                deletedJobUids.add(jobUid);
            } catch (Exception ex) {
                failedItems.add(new BatchDeleteCronJobsDto.FailedItemDto(jobUid,
                        ex.getMessage() == null ? "delete failed" : ex.getMessage()));
            }
        }
        return new BatchDeleteCronJobsDto(requestedCount, deletedJobUids, failedItems);
    }

    public CronJobReportDto getLatestReport(String jobUid) {
        AgentCronJobEntity job = requireJob(jobUid);
        AgentCronJobExecutionEntity latest = agentCronJobExecutionRepository.findLatestFinishedByJobUid(jobUid);
        String reportPathValue = latest == null ? null : latest.getReportPath();
        if (reportPathValue == null || reportPathValue.isBlank()) {
            CronJobDto response = toResponse(job, resolveAgent(job.getAgentUid()));
            reportPathValue = response.lastReportPath();
        }
        if (reportPathValue == null || reportPathValue.isBlank()) {
            return new CronJobReportDto(jobUid, "", "", job.getUpdatedTime());
        }
        try {
            Path reportPath = Path.of(reportPathValue).toAbsolutePath().normalize();
            if (!Files.exists(reportPath)) {
                return new CronJobReportDto(jobUid, reportPath.toString(), "", job.getUpdatedTime());
            }
            return new CronJobReportDto(
                    jobUid,
                    reportPath.toString(),
                    Files.readString(reportPath, StandardCharsets.UTF_8),
                    job.getUpdatedTime()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read cron job report: " + jobUid, ex);
        }
    }

    public List<CronJobExecutionResultDto> listGlobalRecentResults(int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        List<AgentCronJobEntity> jobs = agentCronJobRepository.listAllJobs();
        Map<String, AgentCronJobEntity> jobsByUid = new LinkedHashMap<>();
        for (AgentCronJobEntity job : jobs) {
            jobsByUid.put(job.getJobUid(), job);
        }
        List<AgentCronJobExecutionEntity> recentExecutions = agentCronJobExecutionRepository.listRecent(safeLimit);
        Map<String, AgentDefinitionEntity> agentsByUid = loadAgentsByUids(recentExecutions.stream()
                .map(AgentCronJobExecutionEntity::getAgentUid)
                .filter(uid -> uid != null && !uid.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        List<CronJobExecutionResultDto> all = recentExecutions
                .stream()
                .map(item -> toResultDto(item, jobsByUid.get(item.getJobUid()), agentsByUid.get(item.getAgentUid())))
                .toList();
        if (!all.isEmpty()) {
            return all;
        }
        // One-version compatibility fallback for legacy records in ext_config.
        List<CronJobExecutionResultDto> fallback = new ArrayList<>();
        for (AgentCronJobEntity job : jobs) {
            AgentDefinitionEntity agent = agentsByUid.get(job.getAgentUid());
            fallback.addAll(readLegacyResultsFromJob(job, agent, 50));
        }
        return fallback.stream()
                .sorted(Comparator.comparing(CronJobExecutionResultDto::executedTime).reversed())
                .limit(safeLimit)
                .toList();
    }

    public List<CronJobExecutionResultDto> listGlobalRunningResults(int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        List<AgentCronJobEntity> jobs = agentCronJobRepository.listAllJobs();
        Map<String, AgentCronJobEntity> jobsByUid = new LinkedHashMap<>();
        for (AgentCronJobEntity job : jobs) {
            jobsByUid.put(job.getJobUid(), job);
        }
        List<AgentCronJobExecutionEntity> runningExecutions = agentCronJobExecutionRepository.listRunning(safeLimit);
        Map<String, AgentDefinitionEntity> agentsByUid = loadAgentsByUids(runningExecutions.stream()
                .map(AgentCronJobExecutionEntity::getAgentUid)
                .filter(uid -> uid != null && !uid.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        return runningExecutions.stream()
                .map(item -> toResultDto(item, jobsByUid.get(item.getJobUid()), agentsByUid.get(item.getAgentUid())))
                .toList();
    }

    public PageResult<CronJobExecutionResultDto> listExecutionHistory(String agentUid,
                                                                      String status,
                                                                      String startDate,
                                                                      String endDate,
                                                                      int page,
                                                                      int pageSize) {
        PageRequest normalizedRequest = new PageRequest(page, pageSize).normalize(20, 20);
        if (pageSize > 0 && normalizedRequest.pageSize() != pageSize) {
            throw new IllegalArgumentException("pageSize must be 20");
        }
        String normalizedAgentUid = agentUid == null ? "" : agentUid.trim();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!normalizedStatus.isBlank() && !FINISHED_EXECUTION_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("invalid status: " + status);
        }
        LocalDateTime startTime = parseStartDate(startDate);
        LocalDateTime endTime = parseEndDate(endDate);
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startDate cannot be later than endDate");
        }
        IPage<AgentCronJobExecutionEntity> executionPage = agentCronJobExecutionRepository.pageHistory(
                normalizedAgentUid,
                normalizedStatus,
                startTime,
                endTime,
                normalizedRequest.page(),
                normalizedRequest.pageSize()
        );
        List<AgentCronJobExecutionEntity> executions = executionPage.getRecords();
        List<String> jobUids = executions.stream()
                .map(AgentCronJobExecutionEntity::getJobUid)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        Map<String, AgentCronJobEntity> jobsByUid = new LinkedHashMap<>();
        for (String jobUid : jobUids) {
            AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
            if (job != null) {
                jobsByUid.put(jobUid, job);
            }
        }
        Map<String, AgentDefinitionEntity> agentsByUid = loadAgentsByUids(executions.stream()
                .map(AgentCronJobExecutionEntity::getAgentUid)
                .filter(uid -> uid != null && !uid.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        return PageResultMapper.fromMpPage(executionPage, item -> {
            AgentCronJobEntity job = jobsByUid.get(item.getJobUid());
            String resultAgentUid = nullToEmpty(item.getAgentUid());
            AgentDefinitionEntity agent = agentsByUid.get(resultAgentUid);
            return toResultDto(item, job, agent);
        });
    }

    public CronExecutionDetailDto getExecutionDetail(String executionUid) {
        if (executionUid == null || executionUid.isBlank()) {
            throw new IllegalArgumentException("executionUid is required");
        }
        AgentCronJobExecutionEntity execution = agentCronJobExecutionRepository.findByExecutionUid(executionUid);
        if (execution != null) {
            AgentCronJobEntity job = agentCronJobRepository.findByJobUid(execution.getJobUid());
            AgentDefinitionEntity agent = resolveAgent(execution.getAgentUid());
            CronJobExecutionResultDto item = toResultDto(execution, job, agent);
            List<ConversationMessageRunDto> runs = List.of();
            if (item.conversationUid() != null && !item.conversationUid().isBlank()) {
                runs = resolveConversationRuns(item.conversationUid(), item.messageUid());
            }
            return new CronExecutionDetailDto(
                    item.executionUid(),
                    item.jobUid(),
                    item.jobTitle(),
                    item.agentUid(),
                    item.agentDisplayName(),
                    item.conversationUid(),
                    item.messageUid(),
                    item.status(),
                    item.summary(),
                    item.reportPath(),
                    item.reportContent(),
                    item.executedTime() == null ? "" : item.executedTime().toString(),
                    runs
            );
        }
        // One-version compatibility fallback for legacy records in ext_config.
        List<AgentCronJobEntity> jobs = agentCronJobRepository.listAllJobs();
        Map<String, AgentDefinitionEntity> agentsByUid = loadAgentsByUid(jobs);
        for (AgentCronJobEntity job : jobs) {
            AgentDefinitionEntity agent = agentsByUid.get(job.getAgentUid());
            List<CronJobExecutionResultDto> results = readLegacyResultsFromJob(job, agent, 100);
            for (CronJobExecutionResultDto item : results) {
                if (executionUid.equals(item.executionUid())) {
                    List<ConversationMessageRunDto> runs = List.of();
                    if (item.conversationUid() != null && !item.conversationUid().isBlank()) {
                        runs = resolveConversationRuns(item.conversationUid(), item.messageUid());
                    }
                    return new CronExecutionDetailDto(
                            item.executionUid(),
                            item.jobUid(),
                            item.jobTitle(),
                            item.agentUid(),
                            item.agentDisplayName(),
                            item.conversationUid(),
                            item.messageUid(),
                            item.status(),
                            item.summary(),
                            item.reportPath(),
                            item.reportContent(),
                            item.executedTime() == null ? "" : item.executedTime().toString(),
                            runs
                    );
                }
            }
        }
        throw new IllegalArgumentException("execution not found: " + executionUid);
    }

    public void markExecutionRead(String executionUid) {
        if (executionUid == null || executionUid.isBlank()) {
            throw new IllegalArgumentException("executionUid is required");
        }
        LocalDateTime now = LocalDateTime.now();
        AgentCronJobExecutionEntity execution = agentCronJobExecutionRepository.findByExecutionUid(executionUid);
        if (execution != null) {
            agentCronJobExecutionRepository.markRead(executionUid, now);
            return;
        }
        // One-version compatibility fallback for legacy records in ext_config.
        markLegacyExecutionRead(executionUid, now);
    }

    public List<CronJobExecutionResultDto> listRecentResults(String jobUid, int limit) {
        AgentCronJobEntity job = requireJob(jobUid);
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        AgentDefinitionEntity agent = resolveAgent(job.getAgentUid());
        List<CronJobExecutionResultDto> results = agentCronJobExecutionRepository.listByJobUid(jobUid, safeLimit).stream()
                .map(item -> toResultDto(item, job, agent))
                .toList();
        if (!results.isEmpty()) {
            return results;
        }
        // One-version compatibility fallback for legacy records in ext_config.
        return List.copyOf(readLegacyResultsFromJob(job, agent, safeLimit));
    }

    private List<CronJobExecutionResultDto> readLegacyResultsFromJob(AgentCronJobEntity job,
                                                                     AgentDefinitionEntity agent,
                                                                     int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        if (job.getExtConfig() == null || job.getExtConfig().isBlank()) {
            return List.of();
        }
        JsonNode extNode = JsonUtil.fromJsonQuietly(job.getExtConfig(), JsonNode.class).orElse(JsonNodeFactory.instance.objectNode());
        JsonNode rawResults = extNode.path("executionResults");
        if (!(rawResults instanceof ArrayNode arrayNode) || arrayNode.isEmpty()) {
            return List.of();
        }
        List<CronJobExecutionResultDto> results = new java.util.ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            String executedText = item.path("executedTime").asString("");
            LocalDateTime executedTime;
            try {
                executedTime = LocalDateTime.parse(executedText);
            } catch (Exception ignored) {
                continue;
            }
            String status = item.path("status").asString("");
            String summary = item.path("summary").asString("");
            String reportPath = item.path("reportPath").asString("");
            String normalizedReportPath = reportPath.isBlank() ? null : reportPath;
            String executionUid = item.path("executionUid").asString("");
            String conversationUid = item.path("conversationUid").asString("");
            String messageUid = item.path("messageUid").asString("");
            boolean unread = !item.path("read").asBoolean(false);
            String normalizedExecutionUid = executionUid.isBlank()
                    ? buildLegacyExecutionUid(job.getJobUid(), executedText, conversationUid, messageUid, normalizedReportPath)
                    : executionUid;
            results.add(new CronJobExecutionResultDto(
                    normalizedExecutionUid,
                    unread,
                    job.getJobUid(),
                    defaultCronJobTitle(job.getTitle(), job.getTaskContent()),
                    job.getAgentUid(),
                    agent == null ? buildFallbackAgentDisplayName(job.getAgentUid()) : nullToEmpty(agent.getDisplayName()),
                    conversationUid.isBlank() ? null : conversationUid,
                    messageUid.isBlank() ? null : messageUid,
                    executedTime,
                    status,
                    summary,
                    normalizedReportPath,
                    readReportPreview(normalizedReportPath)
            ));
            if (results.size() >= safeLimit) {
                break;
            }
        }
        return results;
    }

    private CronJobExecutionResultDto toResultDto(AgentCronJobExecutionEntity execution,
                                                  AgentCronJobEntity job,
                                                  AgentDefinitionEntity agent) {
        String executionUid = nullToEmpty(execution.getExecutionUid());
        LocalDateTime executedTime = execution.getStartedTime();
        String status = nullToEmpty(execution.getStatus());
        String summary = nullToEmpty(execution.getSummary());
        String reportPath = nullToEmpty(execution.getReportPath());
        String normalizedReportPath = reportPath.isBlank() ? null : reportPath;
        String conversationUid = nullToEmpty(execution.getConversationUid());
        String messageUid = nullToEmpty(execution.getMessageUid());
        String correctedStatus = reconcileExecutionStatus(status, messageUid);
        if (!correctedStatus.equalsIgnoreCase(status)) {
            status = correctedStatus;
            syncExecutionStatus(executionUid, status);
        }
        String jobUid = job == null ? nullToEmpty(execution.getJobUid()) : job.getJobUid();
        String jobTitle = defaultCronJobTitle(job == null ? "" : job.getTitle(), job == null ? "" : job.getTaskContent());
        String agentUid = nullToEmpty(execution.getAgentUid());
        String agentDisplayName = agent == null ? buildFallbackAgentDisplayName(agentUid) : nullToEmpty(agent.getDisplayName());
        return new CronJobExecutionResultDto(
                executionUid,
                execution.getReadFlag() == null || execution.getReadFlag() != 1,
                jobUid,
                jobTitle,
                agentUid,
                agentDisplayName,
                conversationUid.isBlank() ? null : conversationUid,
                messageUid.isBlank() ? null : messageUid,
                executedTime,
                status,
                summary,
                normalizedReportPath,
                readReportPreview(normalizedReportPath)
        );
    }

    private String reconcileExecutionStatus(String executionStatus, String messageUid) {
        String normalized = executionStatus == null ? "" : executionStatus.trim().toUpperCase();
        if (!List.of("RUNNING", "WAITING_APPROVAL").contains(normalized)) {
            return normalized.isBlank() ? "RUNNING" : normalized;
        }
        String normalizedMessageUid = messageUid == null ? "" : messageUid.trim();
        if (normalizedMessageUid.isBlank()) {
            return normalized;
        }
        try {
            AgentMessage message = agentApplicationService.getMessage(normalizedMessageUid);
            if (message == null || message.status() == null) {
                return normalized;
            }
            return switch (message.status()) {
                case WAITING_APPROVAL -> "WAITING_APPROVAL";
                case RUNNING, CREATED, PLANNED, REPLANNING -> "RUNNING";
                case COMPLETED -> "COMPLETED";
                case FAILED -> "FAILED";
                case CANCELED -> "CANCELED";
            };
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private void syncExecutionStatus(String executionUid, String status) {
        if (executionUid == null || executionUid.isBlank() || status == null || status.isBlank()) {
            return;
        }
        agentCronJobExecutionRepository.lambdaUpdate()
                .eq(AgentCronJobExecutionEntity::getExecutionUid, executionUid)
                .set(AgentCronJobExecutionEntity::getStatus, status)
                .set(AgentCronJobExecutionEntity::getUpdatedTime, LocalDateTime.now())
                .update();
    }

    private List<ConversationMessageRunDto> resolveConversationRuns(String conversationUid, String messageUid) {
        if (conversationUid == null || conversationUid.isBlank()) {
            return List.of();
        }
        try {
            List<ConversationMessageRunDto> runs = conversationAppService.listMessageRuns(conversationUid);
            if (messageUid == null || messageUid.isBlank()) {
                return runs;
            }
            return runs.stream().filter(item -> messageUid.equals(item.messageUid())).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private LocalDateTime parseStartDate(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return LocalDate.parse(normalized).atStartOfDay();
    }

    private LocalDateTime parseEndDate(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return LocalDate.parse(normalized).plusDays(1).atStartOfDay().minusNanos(1);
    }

    private String buildLegacyExecutionUid(String jobUid,
                                           String executedTime,
                                           String conversationUid,
                                           String messageUid,
                                           String reportPath) {
        String seed = String.join("|",
                jobUid == null ? "" : jobUid,
                executedTime == null ? "" : executedTime,
                conversationUid == null ? "" : conversationUid,
                messageUid == null ? "" : messageUid,
                reportPath == null ? "" : reportPath
        );
        return "legacy-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private String readReportPreview(String reportPath) {
        if (reportPath == null || reportPath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(reportPath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return null;
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() <= RESULT_REPORT_PREVIEW_LIMIT) {
                return content;
            }
            return content.substring(0, RESULT_REPORT_PREVIEW_LIMIT) + "\n\n...（预览已截断，点击“打开报告”查看完整内容）";
        } catch (Exception ignored) {
            return null;
        }
    }

    private AgentCronJobEntity requireJob(String jobUid) {
        AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
        if (job == null) {
            throw new IllegalArgumentException("cron job not found: " + jobUid);
        }
        return job;
    }

    private CronJobDto toResponse(AgentCronJobEntity job, AgentDefinitionEntity agent) {
        JsonNode extConfig = job.getExtConfig() == null || job.getExtConfig().isBlank()
                ? JsonNodeFactory.instance.objectNode()
                : JsonUtil.fromJsonQuietly(job.getExtConfig(), JsonNode.class).orElse(JsonNodeFactory.instance.objectNode());
        AgentCronJobExecutionEntity currentExecution = agentCronJobExecutionRepository.findLatestRunningByJobUid(job.getJobUid());
        String currentExecutionUid = "";
        String currentConversationUid = "";
        String currentMessageUid = "";
        String currentExecutionStatus = "";
        LocalDateTime currentExecutionStartedTime = null;
        if (currentExecution != null) {
            currentExecutionUid = nullToEmpty(currentExecution.getExecutionUid());
            currentConversationUid = nullToEmpty(currentExecution.getConversationUid());
            currentMessageUid = nullToEmpty(currentExecution.getMessageUid());
            currentExecutionStatus = nullToEmpty(currentExecution.getStatus());
            currentExecutionStartedTime = currentExecution.getStartedTime();
        } else {
            // One-version compatibility fallback for legacy ext_config.currentExecution.
            JsonNode legacyCurrentExecution = extConfig.path("currentExecution");
            currentExecutionUid = legacyCurrentExecution.path("executionUid").asString("");
            currentConversationUid = legacyCurrentExecution.path("conversationUid").asString("");
            currentMessageUid = legacyCurrentExecution.path("messageUid").asString("");
            currentExecutionStatus = legacyCurrentExecution.path("status").asString("");
            String startedTimeText = legacyCurrentExecution.path("startedTime").asString("");
            if (!startedTimeText.isBlank()) {
                currentExecutionStartedTime = LocalDateTime.parse(startedTimeText);
            }
        }
        String lastReportPath = "";
        AgentCronJobExecutionEntity lastFinished = agentCronJobExecutionRepository.findLatestFinishedByJobUid(job.getJobUid());
        if (lastFinished != null && lastFinished.getReportPath() != null) {
            lastReportPath = lastFinished.getReportPath();
        } else {
            // One-version compatibility fallback for legacy ext_config.lastReportPath.
            lastReportPath = extConfig.path("lastReportPath").asString("");
        }
        return new CronJobDto(
                job.getJobUid(),
                job.getAgentUid(),
                agent == null ? "" : nullToEmpty(agent.getAgentName()),
                agent == null ? buildFallbackAgentDisplayName(job.getAgentUid()) : nullToEmpty(agent.getDisplayName()),
                agent == null ? "" : nullToEmpty(agent.getAvatar()),
                defaultCronJobTitle(job.getTitle(), job.getTaskContent()),
                cronJobSchedulerService.isRegistered(job.getJobUid()),
                cronJobSchedulerService.triggerState(job.getJobUid()),
                job.getExpression(),
                job.getTimezone(),
                readScheduleEndAt(job.getExtConfig()),
                job.getTaskContent(),
                job.getStatus(),
                currentExecutionUid.isBlank() ? null : currentExecutionUid,
                currentConversationUid.isBlank() ? null : currentConversationUid,
                currentMessageUid.isBlank() ? null : currentMessageUid,
                currentExecutionStatus.isBlank() ? null : currentExecutionStatus,
                currentExecutionStartedTime,
                job.getLastRunTime(),
                job.getNextRunTime(),
                job.getLastResult(),
                lastReportPath == null || lastReportPath.isBlank() ? null : lastReportPath,
                job.getCreatedTime(),
                job.getUpdatedTime()
        );
    }

    private void markLegacyExecutionRead(String executionUid, LocalDateTime now) {
        List<AgentCronJobEntity> jobs = agentCronJobRepository.listAllJobs();
        for (AgentCronJobEntity job : jobs) {
            if (job.getExtConfig() == null || job.getExtConfig().isBlank()) {
                continue;
            }
            JsonNode extNode = JsonUtil.fromJsonQuietly(job.getExtConfig(), JsonNode.class)
                    .orElse(JsonNodeFactory.instance.objectNode());
            if (!(extNode instanceof ObjectNode extObject)) {
                continue;
            }
            JsonNode rawResults = extObject.path("executionResults");
            if (!(rawResults instanceof ArrayNode arrayNode) || arrayNode.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (JsonNode item : arrayNode) {
                if (!(item instanceof ObjectNode resultNode)) {
                    continue;
                }
                String itemExecutionUid = resultNode.path("executionUid").asString("");
                if (executionUid.equals(itemExecutionUid)) {
                    resultNode.put("read", true);
                    changed = true;
                    break;
                }
            }
            if (changed) {
                agentCronJobRepository.lambdaUpdate()
                        .eq(AgentCronJobEntity::getJobUid, job.getJobUid())
                        .set(AgentCronJobEntity::getExtConfig, JsonUtil.toJson(extObject))
                        .set(AgentCronJobEntity::getUpdatedTime, now)
                        .update();
                return;
            }
        }
    }

    private Map<String, AgentDefinitionEntity> loadAgentsByUid(List<AgentCronJobEntity> jobs) {
        List<String> agentUids = jobs.stream()
                .map(AgentCronJobEntity::getAgentUid)
                .filter(uid -> uid != null && !uid.isBlank())
                .distinct()
                .toList();
        Map<String, AgentDefinitionEntity> agentsByUid = new LinkedHashMap<>();
        for (AgentDefinitionEntity agent : agentDefinitionRepository.listByUids(agentUids)) {
            agentsByUid.put(agent.getAgentUid(), agent);
        }
        return agentsByUid;
    }

    private Map<String, AgentDefinitionEntity> loadAgentsByUids(LinkedHashSet<String> agentUids) {
        if (agentUids == null || agentUids.isEmpty()) {
            return Map.of();
        }
        Map<String, AgentDefinitionEntity> agentsByUid = new LinkedHashMap<>();
        for (AgentDefinitionEntity agent : agentDefinitionRepository.listByUids(agentUids)) {
            agentsByUid.put(agent.getAgentUid(), agent);
        }
        return agentsByUid;
    }

    private AgentDefinitionEntity resolveAgent(String agentUid) {
        return agentDefinitionRepository.findByUid(agentUid);
    }

    private String normalizeStatus(String requestedStatus, String currentStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return currentStatus == null || currentStatus.isBlank() ? "ACTIVE" : currentStatus.toUpperCase();
        }
        String normalized = requestedStatus.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"PAUSED".equals(normalized)) {
            throw new IllegalArgumentException("unsupported cron job status: " + requestedStatus);
        }
        return normalized;
    }

    private String normalizeCron(String expression) {
        String trimmed = expression.trim().replaceAll("\\s+", " ");
        int fields = trimmed.isEmpty() ? 0 : trimmed.split(" ").length;
        if (fields == 5) {
            return "0 " + trimmed;
        }
        if (fields == 6) {
            return trimmed;
        }
        if (fields == 7) {
            return trimmed;
        }
        throw new IllegalArgumentException("unsupported cron expression fields=" + fields + ", expected 5, 6 or 7");
    }

    private String normalizeEndAt(String endAt) {
        if (endAt == null) {
            return null;
        }
        String trimmed = endAt.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(trimmed).toString();
    }

    private String readScheduleEndAt(String extConfigText) {
        if (extConfigText == null || extConfigText.isBlank()) {
            return null;
        }
        JsonNode extNode = JsonUtil.fromJsonQuietly(extConfigText, JsonNode.class).orElse(JsonNodeFactory.instance.objectNode());
        String endAt = extNode.path("schedule").path("endAt").asString("");
        if (endAt.isBlank()) {
            return null;
        }
        return endAt.trim();
    }

    private void mergeExtConfig(AgentCronJobEntity job, String expression, String timezone, String endAt) {
        JsonNode extNode = job.getExtConfig() == null || job.getExtConfig().isBlank()
                ? JsonNodeFactory.instance.objectNode()
                : JsonUtil.fromJsonQuietly(job.getExtConfig(), JsonNode.class).orElse(JsonNodeFactory.instance.objectNode());
        ObjectNode extConfig = extNode.isObject() ? (ObjectNode) extNode.deepCopy() : JsonNodeFactory.instance.objectNode();
        ObjectNode schedule = extConfig.withObject("schedule");
        schedule.put("expression", expression);
        schedule.put("timezone", timezone);
        if (endAt == null || endAt.isBlank()) {
            schedule.remove("endAt");
        } else {
            schedule.put("endAt", endAt);
        }
        extConfig.withObject("delivery")
                .put("mode", extConfig.path("delivery").path("mode").asString("report_file"))
                .put("format", extConfig.path("delivery").path("format").asString("markdown"));
        ObjectNode notification = extConfig.withObject("notification");
        notification.put("enabled", extConfig.path("notification").path("enabled").asBoolean(true));
        notification.put("channel", extConfig.path("notification").path("channel").asString("noop"));
        notification.put("target", extConfig.path("notification").path("target").asString(""));
        job.setExtConfig(JsonUtil.toJson(extConfig));
    }

    private String buildFallbackAgentDisplayName(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? "未绑定 Agent" : "未知 Agent";
    }

    private String defaultCronJobTitle(String title, String taskContent) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (taskContent == null || taskContent.isBlank()) {
            return "未命名任务";
        }
        String trimmed = taskContent.trim();
        return trimmed.length() <= 48 ? trimmed : trimmed.substring(0, 48) + "...";
    }

    private List<String> normalizeBatchJobUids(List<String> jobUids) {
        if (jobUids == null || jobUids.isEmpty()) {
            throw new IllegalArgumentException("jobUids cannot be empty");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String jobUid : jobUids) {
            if (jobUid == null) {
                continue;
            }
            String trimmed = jobUid.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("jobUids cannot be empty");
        }
        return List.copyOf(normalized);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<CronSubscriptionRepository.CronSubscriptionUpsert> normalizeSubscriptions(
            List<CronSubscriptionRepository.CronSubscriptionUpsert> subscriptions
    ) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return List.of();
        }
        List<CronSubscriptionRepository.CronSubscriptionUpsert> filtered = subscriptions.stream()
                .filter(item -> item != null && item.enabled())
                .toList();
        if (filtered.isEmpty()) {
            return List.of();
        }
        if (filtered.size() > 1) {
            throw new IllegalArgumentException("only one cron channel subscription is allowed");
        }
        CronSubscriptionRepository.CronSubscriptionUpsert item = filtered.get(0);
        ChannelType channelType = ChannelType.from(item.channel());
        if (!isEnabledChannel(channelType)) {
            throw new IllegalArgumentException("channel is not enabled: " + channelType.value());
        }
        String target = item.target() == null ? "" : item.target().trim();
        if (target.isBlank()) {
            target = botCredentialResolver.resolveDefaultTarget(channelType, item.botId());
        }
        if (target.isBlank()) {
            throw new IllegalArgumentException("当前机器人未配置默认推送目标，请先在渠道配置中完成飞书机器人目标解析");
        }
        String normalizedBotId = item.botId() == null ? "" : item.botId().trim();
        if (normalizedBotId.isBlank()) {
            normalizedBotId = botCredentialResolver.resolveDefaultBotId(channelType);
        }
        if (normalizedBotId.isBlank() || !botCredentialResolver.hasEnabledBot(channelType, normalizedBotId)) {
            throw new IllegalArgumentException("未选择可用机器人，请先在渠道配置中启用机器人");
        }
        return List.of(new CronSubscriptionRepository.CronSubscriptionUpsert(
                channelType.value(),
                target,
                normalizedBotId,
                true
        ));
    }

    private boolean isEnabledChannel(ChannelType channelType) {
        return switch (channelType) {
            case FEISHU -> channelsProperties.getFeishu().isEnabled();
            case DINGTALK -> channelsProperties.getDingtalk().isEnabled();
            default -> false;
        };
    }

    private String channelLabel(ChannelType channelType) {
        return switch (channelType) {
            case FEISHU -> "飞书";
            case DINGTALK -> "钉钉";
            default -> channelType.value();
        };
    }
}
