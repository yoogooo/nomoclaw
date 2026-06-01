package ai.nomoclaw.bot.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class CronExecutionResumeWorker {

    private final CronJobExecutionService cronJobExecutionService;
    private final int intervalSeconds;
    private final int batchSize;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile boolean schemaMismatchLogged = false;

    public CronExecutionResumeWorker(CronJobExecutionService cronJobExecutionService,
                                     @Value("${nomoclaw.cron.resume-interval-seconds:3}") int intervalSeconds,
                                     @Value("${nomoclaw.cron.resume-batch-size:100}") int batchSize) {
        this.cronJobExecutionService = cronJobExecutionService;
        this.intervalSeconds = Math.max(intervalSeconds, 1);
        this.batchSize = Math.max(batchSize, 1);
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        while (!Thread.currentThread().isInterrupted()) {
            try {
                cronJobExecutionService.resumeActiveExecutions(batchSize);
            } catch (BadSqlGrammarException ex) {
                // Startup window: code updated but DB migration not applied yet.
                if (!schemaMismatchLogged) {
                    schemaMismatchLogged = true;
                    log.warn("[Cron] execution resume worker paused because schema is outdated: {}", ex.getMostSpecificCause().getMessage());
                } else {
                    log.debug("[Cron] execution resume worker schema mismatch persists");
                }
            } catch (Exception ex) {
                log.warn("[Cron] execution resume worker loop failed err={}", ex.toString());
            } finally {
                try {
                    Thread.sleep(intervalSeconds * 1000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.info("[Cron] execution resume worker interrupted");
                }
            }
        }
    }
}
