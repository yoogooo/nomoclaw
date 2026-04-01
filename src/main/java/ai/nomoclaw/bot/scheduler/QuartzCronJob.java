package ai.nomoclaw.bot.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

@Slf4j
@DisallowConcurrentExecution
public class QuartzCronJob implements Job {

    public static final String JOB_UID = "jobUid";

    private final CronJobExecutionService executionService;

    public QuartzCronJob(CronJobExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String jobUid = dataMap.getString(JOB_UID);
        log.info("[Quartz] trigger jobUid={}", jobUid);
        executionService.executeJob(jobUid);
    }
}
