package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.orchestrator.SystemErrorLogService;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class CronJobSchedulerService {

    private static final String JOB_GROUP = "agent-cron-jobs";
    private static final String TRIGGER_GROUP = "agent-cron-triggers";

    private final ObjectProvider<Scheduler> schedulerProvider;
    private final AgentCronJobRepository agentCronJobRepository;
    private final SystemErrorLogService systemErrorLogService;
    private final int restoreDelaySeconds;
    private final AtomicBoolean restoreStarted = new AtomicBoolean(false);

    public CronJobSchedulerService(ObjectProvider<Scheduler> schedulerProvider,
                                   AgentCronJobRepository agentCronJobRepository,
                                   SystemErrorLogService systemErrorLogService,
                                   @Value("${nomoclaw.quartz.restore-delay-seconds:0}") int restoreDelaySeconds) {
        this.schedulerProvider = schedulerProvider;
        this.agentCronJobRepository = agentCronJobRepository;
        this.systemErrorLogService = systemErrorLogService;
        this.restoreDelaySeconds = Math.max(restoreDelaySeconds, 0);
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void restoreJobsAfterReady() {
        if (!restoreStarted.compareAndSet(false, true)) {
            return;
        }
        long startedAt = System.nanoTime();
        try {
            if (restoreDelaySeconds > 0) {
                log.info("[Quartz] restore deferred delaySeconds={}", restoreDelaySeconds);
                Thread.sleep(restoreDelaySeconds * 1000L);
            }
            for (AgentCronJobEntity job : agentCronJobRepository.listActive()) {
                scheduleJob(job);
            }
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("[Quartz] restore completed elapsedMs={}", elapsedMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[Quartz] restore interrupted");
            systemErrorLogService.recordException(
                    "WARN",
                    "Quartz",
                    "QUARTZ_RESTORE_INTERRUPTED",
                    "Quartz 恢复任务被中断",
                    "应用启动后恢复定时任务时线程被中断。",
                    ex
            );
        } catch (Exception ex) {
            log.warn("[Quartz] failed to restore cron jobs err={}", ex.toString());
            systemErrorLogService.recordException(
                    "ERROR",
                    "Quartz",
                    "QUARTZ_RESTORE_FAILED",
                    "Quartz 恢复任务失败",
                    "应用启动后恢复定时任务失败。",
                    ex
            );
        }
    }

    public LocalDateTime scheduleJob(AgentCronJobEntity cronJob) {
        try {
            Scheduler scheduler = scheduler();
            JobKey jobKey = jobKey(cronJob.getJobUid());
            TriggerKey triggerKey = triggerKey(cronJob.getJobUid());
            JobDataMap dataMap = new JobDataMap();
            dataMap.put(QuartzCronJob.JOB_UID, cronJob.getJobUid());

            JobDetail jobDetail = JobBuilder.newJob(QuartzCronJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(dataMap)
                    .storeDurably()
                    .build();

            CronTrigger trigger = buildTrigger(cronJob, jobKey, triggerKey);

            if (scheduler.checkExists(jobKey)) {
                scheduler.addJob(jobDetail, true, true);
                if (scheduler.checkExists(triggerKey)) {
                    Date next = scheduler.rescheduleJob(triggerKey, trigger);
                    return toLocalDateTime(next, cronJob.getTimezone());
                }
                scheduler.scheduleJob(trigger);
                return toLocalDateTime(trigger.getNextFireTime(), cronJob.getTimezone());
            }

            scheduler.scheduleJob(jobDetail, trigger);
            return toLocalDateTime(trigger.getNextFireTime(), cronJob.getTimezone());
        } catch (SchedulerException ex) {
            systemErrorLogService.recordException(
                    "ERROR",
                    "Quartz",
                    "QUARTZ_SCHEDULE_FAILED",
                    "Quartz 调度任务失败",
                    "定时任务注册到 Quartz Scheduler 失败，jobUid=" + cronJob.getJobUid(),
                    ex
            );
            throw new IllegalStateException("failed to schedule cron job: " + cronJob.getJobUid(), ex);
        }
    }

    public LocalDateTime rescheduleJob(AgentCronJobEntity cronJob) {
        return scheduleJob(cronJob);
    }

    public LocalDateTime pauseJob(String jobUid, String timezone) {
        try {
            Scheduler scheduler = scheduler();
            scheduler.pauseJob(jobKey(jobUid));
            return nextRunTime(jobUid, timezone);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to pause cron job: " + jobUid, ex);
        }
    }

    public LocalDateTime resumeJob(AgentCronJobEntity cronJob) {
        try {
            Scheduler scheduler = scheduler();
            scheduleJob(cronJob);
            scheduler.resumeJob(jobKey(cronJob.getJobUid()));
            return nextRunTime(cronJob.getJobUid(), cronJob.getTimezone());
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to resume cron job: " + cronJob.getJobUid(), ex);
        }
    }

    public void runNow(String jobUid) {
        try {
            Scheduler scheduler = scheduler();
            scheduler.triggerJob(jobKey(jobUid));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to run cron job now: " + jobUid, ex);
        }
    }

    public LocalDateTime nextRunTime(String jobUid, String timezone) {
        try {
            Scheduler scheduler = scheduler();
            Trigger trigger = scheduler.getTrigger(triggerKey(jobUid));
            return trigger == null ? null : toLocalDateTime(trigger.getNextFireTime(), timezone);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to query cron job next run time: " + jobUid, ex);
        }
    }

    public void deleteJob(String jobUid) {
        try {
            Scheduler scheduler = scheduler();
            scheduler.deleteJob(jobKey(jobUid));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to delete cron job: " + jobUid, ex);
        }
    }

    public boolean isRegistered(String jobUid) {
        try {
            Scheduler scheduler = scheduler();
            return scheduler.checkExists(jobKey(jobUid)) || scheduler.checkExists(triggerKey(jobUid));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to check cron job registration: " + jobUid, ex);
        }
    }

    public String triggerState(String jobUid) {
        try {
            Scheduler scheduler = scheduler();
            Trigger.TriggerState state = scheduler.getTriggerState(triggerKey(jobUid));
            return state == null ? "NONE" : state.name();
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to query cron trigger state: " + jobUid, ex);
        }
    }

    private JobKey jobKey(String jobUid) {
        return JobKey.jobKey(jobUid, JOB_GROUP);
    }

    private TriggerKey triggerKey(String jobUid) {
        return TriggerKey.triggerKey(jobUid, TRIGGER_GROUP);
    }

    private Scheduler scheduler() {
        Scheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            throw new IllegalStateException("quartz scheduler is not available");
        }
        return scheduler;
    }

    private CronTrigger buildTrigger(AgentCronJobEntity cronJob, JobKey jobKey, TriggerKey triggerKey) {
        ZoneId zoneId = ZoneId.of(cronJob.getTimezone());
        TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey);
        LocalDateTime endAt = readScheduleEndAt(cronJob.getExtConfig());
        if (endAt != null) {
            builder.endAt(Date.from(endAt.atZone(zoneId).toInstant()));
        }
        return builder
                .withSchedule(CronScheduleBuilder.cronSchedule(cronJob.getExpression())
                        .inTimeZone(java.util.TimeZone.getTimeZone(zoneId))
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    private LocalDateTime readScheduleEndAt(String extConfigText) {
        if (extConfigText == null || extConfigText.isBlank()) {
            return null;
        }
        JsonNode extNode = JsonUtil.fromJsonQuietly(extConfigText, JsonNode.class).orElse(JsonNodeFactory.instance.objectNode());
        String endAt = extNode.path("schedule").path("endAt").asString("");
        if (endAt == null || endAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(endAt.trim());
        } catch (Exception ex) {
            log.warn("[Quartz] ignored invalid schedule.endAt jobUid={} value={}", extNode.path("jobUid").asString(""), endAt);
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Date date, String timezone) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.of(timezone));
    }
}
