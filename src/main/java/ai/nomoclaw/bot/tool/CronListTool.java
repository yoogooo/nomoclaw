package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Component
public class CronListTool implements Tool {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final AgentCronJobRepository agentCronJobRepository;

    public CronListTool(AgentCronJobRepository agentCronJobRepository) {
        this.agentCronJobRepository = agentCronJobRepository;
    }

    @Override
    public String name() {
        return "CronListTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String jobUid = request.args().path("jobUid").asText("").trim();
            String status = request.args().path("status").asText("").trim();
            int limit = normalizeLimit(request.args().path("limit").asInt(DEFAULT_LIMIT));

            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            ArrayNode jobs = artifacts.putArray("jobs");
            if (!jobUid.isBlank()) {
                AgentCronJobEntity job = agentCronJobRepository.findByJobUid(jobUid);
                if (job == null) {
                    return ToolResult.failure("CRON_NOT_FOUND", "cron job not found: " + jobUid, metrics(start, false));
                }
                jobs.add(toArtifact(job));
                artifacts.put("count", 1);
                artifacts.put("jobUid", jobUid);
                return ToolResult.success("cron job found", artifacts, metrics(start, true));
            }

            String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
            List<AgentCronJobEntity> allJobs = agentCronJobRepository.listAllJobs();
            int count = 0;
            for (AgentCronJobEntity job : allJobs) {
                if (!normalizedStatus.isBlank() && !normalizedStatus.equals(nullToEmpty(job.getStatus()).toUpperCase(Locale.ROOT))) {
                    continue;
                }
                if (count >= limit) {
                    break;
                }
                jobs.add(toArtifact(job));
                count++;
            }
            artifacts.put("count", count);
            artifacts.put("limit", limit);
            artifacts.put("status", normalizedStatus);
            return ToolResult.success("cron jobs listed", artifacts, metrics(start, true));
        } catch (Exception ex) {
            return ToolResult.failure("CRON_LIST_ERROR", ex.getMessage(), metrics(start, false));
        }
    }

    private int normalizeLimit(int value) {
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private ObjectNode toArtifact(AgentCronJobEntity job) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("jobUid", nullToEmpty(job.getJobUid()));
        node.put("agentUid", nullToEmpty(job.getAgentUid()));
        node.put("title", nullToEmpty(job.getTitle()));
        node.put("expression", nullToEmpty(job.getExpression()));
        node.put("timezone", nullToEmpty(job.getTimezone()));
        node.put("task", nullToEmpty(job.getTaskContent()));
        node.put("status", nullToEmpty(job.getStatus()));
        node.put("lastRunTime", format(job.getLastRunTime()));
        node.put("nextRunTime", format(job.getNextRunTime()));
        node.put("lastResult", nullToEmpty(job.getLastResult()));
        node.put("createdTime", format(job.getCreatedTime()));
        node.put("updatedTime", format(job.getUpdatedTime()));
        return node;
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private ObjectNode metrics(long start, boolean success) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("success", success);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
