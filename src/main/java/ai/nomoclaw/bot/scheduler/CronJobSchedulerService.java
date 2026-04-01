package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@DependsOn("cronJobSchemaInitializer")
@Slf4j
public class CronJobSchedulerService {

    private static final String JOB_GROUP = "agent-cron-jobs";
    private static final String TRIGGER_GROUP = "agent-cron-triggers";

    private final Scheduler scheduler;
    private final AgentCronJobRepository agentCronJobRepository;

    public CronJobSchedulerService(Scheduler scheduler,
                                   AgentCronJobRepository agentCronJobRepository) {
        this.scheduler = scheduler;
        this.agentCronJobRepository = agentCronJobRepository;
    }

    @PostConstruct
    public void restoreJobs() {
        try {
            for (AgentCronJobEntity job : agentCronJobRepository.listActive()) {
                scheduleJob(job);
            }
        } catch (Exception ex) {
            log.warn("[Quartz] failed to restore cron jobs err={}", ex.toString());
        }
    }

    public LocalDateTime scheduleJob(AgentCronJobEntity cronJob) {
        try {
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
            throw new IllegalStateException("failed to schedule cron job: " + cronJob.getJobUid(), ex);
        }
    }

    public LocalDateTime rescheduleJob(AgentCronJobEntity cronJob) {
        return scheduleJob(cronJob);
    }

    public LocalDateTime pauseJob(String jobUid, String timezone) {
        try {
            scheduler.pauseJob(jobKey(jobUid));
            return nextRunTime(jobUid, timezone);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to pause cron job: " + jobUid, ex);
        }
    }

    public LocalDateTime resumeJob(AgentCronJobEntity cronJob) {
        try {
            scheduleJob(cronJob);
            scheduler.resumeJob(jobKey(cronJob.getJobUid()));
            return nextRunTime(cronJob.getJobUid(), cronJob.getTimezone());
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to resume cron job: " + cronJob.getJobUid(), ex);
        }
    }

    public void runNow(String jobUid) {
        try {
            scheduler.triggerJob(jobKey(jobUid));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to run cron job now: " + jobUid, ex);
        }
    }

    public LocalDateTime nextRunTime(String jobUid, String timezone) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey(jobUid));
            return trigger == null ? null : toLocalDateTime(trigger.getNextFireTime(), timezone);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to query cron job next run time: " + jobUid, ex);
        }
    }

    public void deleteJob(String jobUid) {
        try {
            scheduler.deleteJob(jobKey(jobUid));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to delete cron job: " + jobUid, ex);
        }
    }

    public boolean isRegistered(String jobUid) {
        try {
            return scheduler.checkExists(jobKey(jobUid)) || scheduler.checkExists(triggerKey(jobUid));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("failed to check cron job registration: " + jobUid, ex);
        }
    }

    public String triggerState(String jobUid) {
        try {
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
        String endAt = extNode.path("schedule").path("endAt").asText("");
        if (endAt == null || endAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(endAt.trim());
        } catch (Exception ex) {
            log.warn("[Quartz] ignored invalid schedule.endAt jobUid={} value={}", extNode.path("jobUid").asText(""), endAt);
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
