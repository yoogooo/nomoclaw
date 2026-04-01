package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.application.dto.BatchDeleteCronJobsDto;
import ai.nomoclaw.bot.application.dto.CronJobReportDto;
import ai.nomoclaw.bot.application.dto.CronJobDto;
import ai.nomoclaw.bot.application.dto.CronJobExecutionResultDto;
import ai.nomoclaw.bot.application.dto.CronSubscriptionDto;
import ai.nomoclaw.bot.application.command.CreateCronJobCommand;
import ai.nomoclaw.bot.application.command.UpdateCronJobCommand;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CronJobApplicationService {
    private static final int RESULT_REPORT_PREVIEW_LIMIT = 16_000;

    private final AgentCronJobRepository agentCronJobRepository;
    private final CronJobSchedulerService cronJobSchedulerService;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final CronSubscriptionRepository cronSubscriptionRepository;
    private final AgentChannelsProperties channelsProperties;
    private final CronChannelTargetResolver channelTargetResolver;

    public CronJobApplicationService(AgentCronJobRepository agentCronJobRepository,
                                     CronJobSchedulerService cronJobSchedulerService,
                                     AgentDefinitionRepository agentDefinitionRepository,
                                     CronSubscriptionRepository cronSubscriptionRepository,
                                     AgentChannelsProperties channelsProperties,
                                     CronChannelTargetResolver channelTargetResolver) {
        this.agentCronJobRepository = agentCronJobRepository;
        this.cronJobSchedulerService = cronJobSchedulerService;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.cronSubscriptionRepository = cronSubscriptionRepository;
        this.channelsProperties = channelsProperties;
        this.channelTargetResolver = channelTargetResolver;
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

    public CronJobDto createCronJob(CreateCronJobCommand request) {
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

    public CronJobDto updateCronJob(String jobUid, UpdateCronJobCommand request) {
        if (request == null) {
            request = new UpdateCronJobCommand(null, null, null, null, null, null);
        }
        AgentCronJobEntity job = requireJob(jobUid);
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
        return toResponse(job, resolveAgent(job.getAgentUid()));
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
        AgentCronJobEntity job = requireJob(jobUid);
        cronJobSchedulerService.runNow(jobUid);
        return toResponse(job, resolveAgent(job.getAgentUid()));
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
        CronJobDto response = toResponse(job, resolveAgent(job.getAgentUid()));
        String reportPathValue = response.lastReportPath();
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

    public List<CronJobExecutionResultDto> listRecentResults(String jobUid, int limit) {
        AgentCronJobEntity job = requireJob(jobUid);
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
            String executedText = item.path("executedTime").asText("");
            LocalDateTime executedTime;
            try {
                executedTime = LocalDateTime.parse(executedText);
            } catch (Exception ignored) {
                continue;
            }
            String status = item.path("status").asText("");
            String summary = item.path("summary").asText("");
            String reportPath = item.path("reportPath").asText("");
            String normalizedReportPath = reportPath.isBlank() ? null : reportPath;
            results.add(new CronJobExecutionResultDto(
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
        return List.copyOf(results);
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
        String lastReportPath = extConfig.path("lastReportPath").asText("");
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
                job.getLastRunTime(),
                job.getNextRunTime(),
                job.getLastResult(),
                lastReportPath.isBlank() ? null : lastReportPath,
                job.getCreatedTime(),
                job.getUpdatedTime()
        );
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
        String endAt = extNode.path("schedule").path("endAt").asText("");
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
                .put("mode", extConfig.path("delivery").path("mode").asText("report_file"))
                .put("format", extConfig.path("delivery").path("format").asText("markdown"));
        ObjectNode notification = extConfig.withObject("notification");
        notification.put("enabled", extConfig.path("notification").path("enabled").asBoolean(true));
        notification.put("channel", extConfig.path("notification").path("channel").asText("noop"));
        notification.put("target", extConfig.path("notification").path("target").asText(""));
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
            target = channelTargetResolver.resolveLatestReplyTarget(channelType.value());
        }
        if (target.isBlank()) {
            throw new IllegalArgumentException(channelLabel(channelType) + " 订阅缺少可用推送目标，请先在该渠道与机器人产生一次会话，或填写 webhook/session target");
        }
        return List.of(new CronSubscriptionRepository.CronSubscriptionUpsert(
                channelType.value(),
                target,
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
