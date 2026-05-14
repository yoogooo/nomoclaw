package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.orchestrator.ApprovalGrantedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CronApprovalResumeSignalListener {

    private final CronJobExecutionService cronJobExecutionService;

    public CronApprovalResumeSignalListener(CronJobExecutionService cronJobExecutionService) {
        this.cronJobExecutionService = cronJobExecutionService;
    }

    @EventListener
    public void onApprovalGranted(ApprovalGrantedEvent event) {
        if (event == null || event.messageUid() == null || event.messageUid().isBlank()) {
            return;
        }
        cronJobExecutionService.signalResumeByMessageUid(event.messageUid());
    }
}
