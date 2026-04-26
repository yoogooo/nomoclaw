package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.scheduler.CronJobSchedulerService;
import ai.nomoclaw.bot.scheduler.CronSubscriptionRepository;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

@Component
public class CronDeleteTool implements Tool {

    private final AgentCronJobRepository agentCronJobRepository;
    private final CronJobSchedulerService cronJobSchedulerService;
    private final CronSubscriptionRepository cronSubscriptionRepository;

    public CronDeleteTool(AgentCronJobRepository agentCronJobRepository,
                          CronJobSchedulerService cronJobSchedulerService,
                          CronSubscriptionRepository cronSubscriptionRepository) {
        this.agentCronJobRepository = agentCronJobRepository;
        this.cronJobSchedulerService = cronJobSchedulerService;
        this.cronSubscriptionRepository = cronSubscriptionRepository;
    }

    @Override
    public String name() {
        return "CronDeleteTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String jobUid = request.args().path("jobUid").asText("").trim();
            if (jobUid.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "jobUid is required", metrics(start, false));
            }
            AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
            if (job == null) {
                return ToolResult.failure("CRON_NOT_FOUND", "cron job not found: " + jobUid, metrics(start, false));
            }

            cronJobSchedulerService.deleteJob(jobUid);
            cronSubscriptionRepository.deleteByJobUid(jobUid);
            agentCronJobRepository.removeById(job.getId());

            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("jobUid", jobUid);
            artifacts.put("deleted", true);
            artifacts.put("title", job.getTitle() == null ? "" : job.getTitle());
            return ToolResult.success("cron job deleted", artifacts, metrics(start, true));
        } catch (Exception ex) {
            return ToolResult.failure("CRON_DELETE_ERROR", ex.getMessage(), metrics(start, false));
        }
    }

    private ObjectNode metrics(long start, boolean success) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("success", success);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
