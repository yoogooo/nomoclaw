package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.scheduler.CronJobSchedulerService;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

@Component
public class CronCreateTool implements Tool {

    private final AgentCronJobRepository agentCronJobRepository;
    private final CronJobSchedulerService cronJobSchedulerService;

    public CronCreateTool(AgentCronJobRepository agentCronJobRepository,
                          CronJobSchedulerService cronJobSchedulerService) {
        this.agentCronJobRepository = agentCronJobRepository;
        this.cronJobSchedulerService = cronJobSchedulerService;
    }

    @Override
    public String name() {
        return "CronCreateTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String title = request.args().path("title").asString("");
            String expression = request.args().path("expression").asString("");
            String timezone = request.args().path("timezone").asString("Asia/Shanghai");
            String task = request.args().path("task").asString("");
            if (expression.isBlank() || task.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "expression and task are required", metrics(start, false));
            }
            if (request.agentUid() == null || request.agentUid().isBlank()) {
                return ToolResult.failure("INVALID_AGENT", "cron task must be bound to a concrete agent", metrics(start, false));
            }

            String normalizedCron = normalizeCron(expression);
            ZoneId zoneId = ZoneId.of(timezone);
            String jobId = UUID.randomUUID().toString();
            AgentCronJobEntity job = new AgentCronJobEntity();
            job.setJobUid(jobId);
            job.setAgentUid(request.agentUid() == null ? "" : request.agentUid());
            job.setConversationUid(request.conversationUid());
            job.setMessageUid(request.messageUid());
            job.setTitle(resolveTitle(title, task));
            job.setExpression(normalizedCron);
            job.setTimezone(zoneId.toString());
            job.setTaskContent(task);
            job.setStatus("ACTIVE");
            job.setLastRunTime(null);
            job.setNextRunTime(nextRunTime(normalizedCron, zoneId));
            job.setLastResult("");
            job.setExtConfig(buildExtConfig(request, normalizedCron, zoneId));
            job.setCreatedTime(LocalDateTime.now());
            job.setUpdatedTime(LocalDateTime.now());
            agentCronJobRepository.save(job);
            LocalDateTime nextRunTime = cronJobSchedulerService.scheduleJob(job);
            job.setNextRunTime(nextRunTime);
            agentCronJobRepository.updateById(job);

            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("jobUid", jobId);
            artifacts.put("title", job.getTitle());
            artifacts.put("expression", normalizedCron);
            artifacts.put("timezone", zoneId.toString());
            artifacts.put("task", task);
            artifacts.put("status", "ACTIVE");
            artifacts.put("nextRunTime", nextRunTime == null ? "" : nextRunTime.toString());
            return ToolResult.success("cron job created", artifacts, metrics(start, true));
        } catch (Exception ex) {
            return ToolResult.failure("CRON_ERROR", ex.getMessage(), metrics(start, false));
        }
    }

    private String normalizeCron(String expression) {
        String trimmed = expression.trim().replaceAll("\\s+", " ");
        String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split(" ");
        int fields = parts.length;
        if (fields == 5) {
            // Convert Unix-style minute hour day-of-month month day-of-week to Quartz.
            String dayOfWeek = "*".equals(parts[4]) ? "?" : parts[4];
            return "0 %s %s %s %s %s".formatted(parts[0], parts[1], parts[2], parts[3], dayOfWeek);
        }
        if (fields == 6) {
            return trimmed;
        }
        if (fields == 7) {
            return trimmed;
        }
        throw new IllegalArgumentException("unsupported cron expression fields=" + fields + ", expected 5, 6 or 7");
    }

    private LocalDateTime nextRunTime(String expression, ZoneId zoneId) {
        try {
            org.quartz.CronExpression cronExpression = new org.quartz.CronExpression(expression);
            cronExpression.setTimeZone(TimeZone.getTimeZone(zoneId));
            Date next = cronExpression.getNextValidTimeAfter(Date.from(Instant.now()));
            return next == null ? null : LocalDateTime.ofInstant(next.toInstant(), zoneId);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid cron expression: " + ex.getMessage(), ex);
        }
    }

    private String buildExtConfig(ToolRequest request, String normalizedCron, ZoneId zoneId) {
        ObjectNode extConfig = JsonNodeFactory.instance.objectNode();
        extConfig.put("stepUid", request.stepUid());

        ObjectNode schedule = extConfig.putObject("schedule");
        schedule.put("expression", normalizedCron);
        schedule.put("timezone", zoneId.toString());

        ObjectNode delivery = extConfig.putObject("delivery");
        delivery.put("mode", "report_file");
        delivery.put("format", "markdown");

        ObjectNode notification = extConfig.putObject("notification");
        notification.put("enabled", true);
        notification.put("channel", "noop");
        notification.put("target", "");

        return extConfig.toString();
    }

    private String resolveTitle(String title, String task) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String trimmed = task == null ? "" : task.trim();
        if (trimmed.isBlank()) {
            return "未命名任务";
        }
        return trimmed.length() <= 48 ? trimmed : trimmed.substring(0, 48) + "...";
    }

    private ObjectNode metrics(long start, boolean success) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("success", success);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
