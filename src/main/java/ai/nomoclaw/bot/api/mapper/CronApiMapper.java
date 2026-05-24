package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.common.response.PageResponse;
import ai.nomoclaw.bot.api.dto.cron.request.CreateCronJobRequest;
import ai.nomoclaw.bot.api.dto.cron.request.UpdateCronJobRequest;
import ai.nomoclaw.bot.api.dto.cron.request.UpdateCronSubscriptionsRequest;
import ai.nomoclaw.bot.api.dto.cron.response.BatchDeleteCronJobsResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronExecutionDetailResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronJobExecutionResultResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronJobReportResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronJobResponse;
import ai.nomoclaw.bot.api.dto.cron.response.CronSubscriptionResponse;
import ai.nomoclaw.bot.scheduler.model.CreateCronJobParam;
import ai.nomoclaw.bot.scheduler.model.UpdateCronJobParam;
import ai.nomoclaw.bot.common.page.PageResult;
import ai.nomoclaw.bot.scheduler.model.BatchDeleteCronJobsDto;
import ai.nomoclaw.bot.scheduler.model.CronExecutionDetailDto;
import ai.nomoclaw.bot.scheduler.model.CronJobDto;
import ai.nomoclaw.bot.scheduler.model.CronJobExecutionResultDto;
import ai.nomoclaw.bot.scheduler.model.CronJobReportDto;
import ai.nomoclaw.bot.scheduler.model.CronSubscriptionDto;
import ai.nomoclaw.bot.scheduler.CronSubscriptionRepository;

import java.util.List;
import java.util.Objects;

/**
 * Maps Cron domain request/response payloads.
 */
public final class CronApiMapper {

    private CronApiMapper() {
    }

    public static List<CronJobResponse> toCronJobs(List<CronJobDto> dtos) {
        return dtos.stream().map(CronApiMapper::toCronJob).toList();
    }

    public static CronJobResponse toCronJob(CronJobDto dto) {
        return new CronJobResponse(
                dto.jobUid(),
                dto.agentUid(),
                dto.agentName(),
                dto.agentDisplayName(),
                dto.agentAvatar(),
                dto.title(),
                dto.registered(),
                dto.triggerState(),
                dto.expression(),
                dto.timezone(),
                dto.endAt(),
                dto.modelProvider(),
                dto.modelName(),
                dto.taskContent(),
                dto.status(),
                dto.currentExecutionUid(),
                dto.currentConversationUid(),
                dto.currentMessageUid(),
                dto.currentExecutionStatus(),
                dto.currentExecutionStartedTime(),
                dto.lastRunTime(),
                dto.nextRunTime(),
                dto.lastResult(),
                dto.lastReportPath(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static CronJobReportResponse toCronJobReport(CronJobReportDto dto) {
        return new CronJobReportResponse(dto.jobUid(), dto.reportPath(), dto.content(), dto.updatedTime());
    }

    public static List<CronJobExecutionResultResponse> toCronJobExecutionResults(List<CronJobExecutionResultDto> dtos) {
        return dtos.stream()
                .map(item -> new CronJobExecutionResultResponse(
                        item.executionUid(),
                        item.unread(),
                        item.jobUid(),
                        item.jobTitle(),
                        item.agentUid(),
                        item.agentDisplayName(),
                        item.conversationUid(),
                        item.messageUid(),
                        item.executedTime(),
                        item.status(),
                        item.summary(),
                        item.reportPath(),
                        item.reportContent()
                ))
                .toList();
    }

    public static PageResponse<CronJobExecutionResultResponse> toCronJobExecutionHistoryPage(PageResult<CronJobExecutionResultDto> dto) {
        return new PageResponse<>(
                toCronJobExecutionResults(dto.items()),
                dto.total(),
                dto.page(),
                dto.pageSize(),
                dto.totalPages()
        );
    }

    public static CronExecutionDetailResponse toCronExecutionDetail(CronExecutionDetailDto dto) {
        return new CronExecutionDetailResponse(
                dto.executionUid(),
                dto.jobUid(),
                dto.jobTitle(),
                dto.agentUid(),
                dto.agentDisplayName(),
                dto.conversationUid(),
                dto.messageUid(),
                dto.status(),
                dto.summary(),
                dto.reportPath(),
                dto.reportContent(),
                dto.executedTime(),
                dto.runs().stream().map(ConversationApiMapper::toMessageRun).toList()
        );
    }

    public static BatchDeleteCronJobsResponse toBatchDeleteResult(BatchDeleteCronJobsDto dto) {
        return new BatchDeleteCronJobsResponse(
                dto.requestedCount(),
                dto.deletedJobUids(),
                dto.failedItems().stream()
                        .map(item -> new BatchDeleteCronJobsResponse.FailedItem(item.jobUid(), item.reason()))
                        .toList()
        );
    }

    public static List<CronSubscriptionResponse> toCronSubscriptions(List<CronSubscriptionDto> dtos) {
        return dtos.stream().map(CronApiMapper::toCronSubscription).toList();
    }

    public static CronSubscriptionResponse toCronSubscription(CronSubscriptionDto dto) {
        return new CronSubscriptionResponse(
                dto.subscriptionUid(),
                dto.jobUid(),
                dto.channel(),
                dto.target(),
                dto.botId(),
                dto.enabled(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static CreateCronJobParam toParam(CreateCronJobRequest request) {
        return new CreateCronJobParam(
                request == null ? null : request.agentUid(),
                request == null ? null : request.title(),
                request == null ? null : request.expression(),
                request == null ? null : request.timezone(),
                request == null ? null : request.endAt(),
                request == null ? null : request.modelProvider(),
                request == null ? null : request.modelName(),
                request == null ? null : request.taskContent(),
                request == null ? null : request.status()
        );
    }

    public static UpdateCronJobParam toParam(UpdateCronJobRequest request) {
        return new UpdateCronJobParam(
                request == null ? null : request.agentUid(),
                request == null ? null : request.title(),
                request == null ? null : request.expression(),
                request == null ? null : request.timezone(),
                request == null ? null : request.endAt(),
                request == null ? null : request.modelProvider(),
                request == null ? null : request.modelName(),
                request == null ? null : request.taskContent(),
                request == null ? null : request.status()
        );
    }

    public static List<CronSubscriptionRepository.CronSubscriptionUpsert> toCronSubscriptions(UpdateCronSubscriptionsRequest request) {
        if (request == null || request.subscriptions() == null) {
            return List.of();
        }
        return request.subscriptions().stream()
                .filter(Objects::nonNull)
                .map(item -> new CronSubscriptionRepository.CronSubscriptionUpsert(
                        item.channel(),
                        item.target(),
                        item.botId(),
                        item.enabled()
                ))
                .toList();
    }
}
