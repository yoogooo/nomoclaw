package ai.nomoclaw.bot.api.controller.cron;

import ai.nomoclaw.bot.api.dto.common.response.PageResponse;
import ai.nomoclaw.bot.api.dto.common.response.SimpleResponse;
import ai.nomoclaw.bot.api.dto.cron.request.BatchDeleteCronJobsRequest;
import ai.nomoclaw.bot.api.dto.cron.request.CreateCronJobRequest;
import ai.nomoclaw.bot.api.dto.cron.request.UpdateCronJobRequest;
import ai.nomoclaw.bot.api.dto.cron.request.UpdateCronSubscriptionsRequest;
import ai.nomoclaw.bot.api.dto.cron.response.BatchDeleteCronJobsResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronExecutionDetailResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronJobExecutionResultResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronJobReportResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronJobResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronSubscriptionResponse;
import ai.nomoclaw.bot.api.mapper.CronApiMapper;
import ai.nomoclaw.bot.scheduler.CronJobApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cron job management endpoints.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class CronController {

    private final CronJobApplicationService cronJobApplicationService;

    public CronController(CronJobApplicationService cronJobApplicationService) {
        this.cronJobApplicationService = cronJobApplicationService;
    }

    @GetMapping("/cron-jobs")
    public List<CronJobResponse> listCronJobs() {
        log.info("[AgentAPI] listCronJobs");
        return CronApiMapper.toCronJobs(cronJobApplicationService.listCronJobs());
    }

    @PostMapping("/cron-jobs")
    public CronJobResponse createCronJob(@RequestBody(required = false) CreateCronJobRequest request) {
        log.info("[AgentAPI] createCronJob agentUid={} title={} expression={} timezone={} status={}",
                request == null ? null : request.agentUid(),
                request == null ? null : request.title(),
                request == null ? null : request.expression(),
                request == null ? null : request.timezone(),
                request == null ? null : request.status());
        return CronApiMapper.toCronJob(cronJobApplicationService.createCronJob(CronApiMapper.toParam(request)));
    }

    @GetMapping("/cron-jobs/{jobUid}")
    public CronJobResponse getCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] getCronJob jobUid={}", jobUid);
        return CronApiMapper.toCronJob(cronJobApplicationService.getCronJob(jobUid));
    }

    @GetMapping("/cron-jobs/{jobUid}/report")
    public CronJobReportResponse getCronJobReport(@PathVariable String jobUid) {
        log.info("[AgentAPI] getCronJobReport jobUid={}", jobUid);
        return CronApiMapper.toCronJobReport(cronJobApplicationService.getLatestReport(jobUid));
    }

    @GetMapping("/cron-jobs/{jobUid}/results")
    public List<CronJobExecutionResultResponse> listCronJobResults(@PathVariable String jobUid) {
        log.info("[AgentAPI] listCronJobResults jobUid={}", jobUid);
        return CronApiMapper.toCronJobExecutionResults(cronJobApplicationService.listRecentResults(jobUid, 20));
    }

    @GetMapping("/cron-jobs/results/recent")
    public List<CronJobExecutionResultResponse> listRecentCronJobResults(@RequestParam(required = false, defaultValue = "20") int limit) {
        log.info("[AgentAPI] listRecentCronJobResults limit={}", limit);
        return CronApiMapper.toCronJobExecutionResults(cronJobApplicationService.listGlobalRecentResults(limit));
    }

    @GetMapping("/cron-jobs/results/running")
    public List<CronJobExecutionResultResponse> listRunningCronJobResults(@RequestParam(required = false, defaultValue = "50") int limit) {
        log.info("[AgentAPI] listRunningCronJobResults limit={}", limit);
        return CronApiMapper.toCronJobExecutionResults(cronJobApplicationService.listGlobalRunningResults(limit));
    }

    @GetMapping("/cron-jobs/results/history")
    public PageResponse<CronJobExecutionResultResponse> listCronJobExecutionHistory(
            @RequestParam(required = false, defaultValue = "") String agentUid,
            @RequestParam(required = false, defaultValue = "") String status,
            @RequestParam(required = false, defaultValue = "") String startDate,
            @RequestParam(required = false, defaultValue = "") String endDate,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        log.info("[AgentAPI] listCronJobExecutionHistory agentUid={} status={} startDate={} endDate={} page={} pageSize={}",
                agentUid, status, startDate, endDate, page, pageSize);
        return CronApiMapper.toCronJobExecutionHistoryPage(
                cronJobApplicationService.listExecutionHistory(agentUid, status, startDate, endDate, page, pageSize)
        );
    }

    @GetMapping("/cron-jobs/executions/{executionUid}")
    public CronExecutionDetailResponse getCronExecutionDetail(@PathVariable String executionUid) {
        log.info("[AgentAPI] getCronExecutionDetail executionUid={}", executionUid);
        return CronApiMapper.toCronExecutionDetail(cronJobApplicationService.getExecutionDetail(executionUid));
    }

    @PostMapping("/cron-jobs/executions/{executionUid}/read")
    public SimpleResponse markCronExecutionRead(@PathVariable String executionUid) {
        log.info("[AgentAPI] markCronExecutionRead executionUid={}", executionUid);
        cronJobApplicationService.markExecutionRead(executionUid);
        return new SimpleResponse("updated");
    }

    @PostMapping("/cron-jobs/{jobUid}/run")
    public CronJobResponse runCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] runCronJob jobUid={}", jobUid);
        return CronApiMapper.toCronJob(cronJobApplicationService.runCronJob(jobUid));
    }

    @PatchMapping("/cron-jobs/{jobUid}")
    public CronJobResponse updateCronJob(@PathVariable String jobUid,
                                         @RequestBody(required = false) UpdateCronJobRequest request) {
        log.info("[AgentAPI] updateCronJob jobUid={} agentUid={} title={} expression={} timezone={} taskContent={} status={}",
                jobUid,
                request == null ? null : request.agentUid(),
                request == null ? null : request.title(),
                request == null ? null : request.expression(),
                request == null ? null : request.timezone(),
                request == null ? null : request.taskContent(),
                request == null ? null : request.status());
        return CronApiMapper.toCronJob(cronJobApplicationService.updateCronJob(jobUid, CronApiMapper.toParam(request)));
    }

    @PostMapping("/cron-jobs/{jobUid}/pause")
    public CronJobResponse pauseCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] pauseCronJob jobUid={}", jobUid);
        return CronApiMapper.toCronJob(cronJobApplicationService.pauseCronJob(jobUid));
    }

    @PostMapping("/cron-jobs/{jobUid}/resume")
    public CronJobResponse resumeCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] resumeCronJob jobUid={}", jobUid);
        return CronApiMapper.toCronJob(cronJobApplicationService.resumeCronJob(jobUid));
    }

    @DeleteMapping("/cron-jobs/{jobUid}")
    public SimpleResponse deleteCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] deleteCronJob jobUid={}", jobUid);
        cronJobApplicationService.deleteCronJob(jobUid);
        return new SimpleResponse("deleted");
    }

    @GetMapping("/cron-jobs/{jobUid}/subscriptions")
    public List<CronSubscriptionResponse> listCronSubscriptions(@PathVariable String jobUid) {
        log.info("[AgentAPI] listCronSubscriptions jobUid={}", jobUid);
        return CronApiMapper.toCronSubscriptions(cronJobApplicationService.listCronSubscriptions(jobUid));
    }

    @PutMapping("/cron-jobs/{jobUid}/subscriptions")
    public List<CronSubscriptionResponse> updateCronSubscriptions(@PathVariable String jobUid,
                                                                  @RequestBody(required = false) UpdateCronSubscriptionsRequest request) {
        int requestedCount = request == null || request.subscriptions() == null ? 0 : request.subscriptions().size();
        log.info("[AgentAPI] updateCronSubscriptions jobUid={} requestedCount={}", jobUid, requestedCount);
        return CronApiMapper.toCronSubscriptions(
                cronJobApplicationService.updateCronSubscriptions(jobUid, CronApiMapper.toCronSubscriptions(request))
        );
    }

    @PostMapping("/cron-jobs/batch-delete")
    public BatchDeleteCronJobsResponse batchDeleteCronJobs(@RequestBody(required = false) BatchDeleteCronJobsRequest request) {
        int requestedCount = request == null || request.jobUids() == null ? 0 : request.jobUids().size();
        log.info("[AgentAPI] batchDeleteCronJobs requestedCount={}", requestedCount);
        return CronApiMapper.toBatchDeleteResult(cronJobApplicationService.batchDeleteCronJobs(
                request == null ? List.of() : request.jobUids()
        ));
    }
}
