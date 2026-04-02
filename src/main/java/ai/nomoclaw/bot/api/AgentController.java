package ai.nomoclaw.bot.api;

import ai.nomoclaw.bot.orchestrator.AgentCatalogAppService;
import ai.nomoclaw.bot.orchestrator.ApprovalAppService;
import ai.nomoclaw.bot.orchestrator.ConversationAttachmentAppService;
import ai.nomoclaw.bot.orchestrator.ConversationAppService;
import ai.nomoclaw.bot.orchestrator.MessageRunAppService;
import ai.nomoclaw.bot.orchestrator.ModelConfigAppService;
import ai.nomoclaw.bot.orchestrator.SystemAppService;
import ai.nomoclaw.bot.scheduler.CronJobApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api")
@Slf4j
public class AgentController {

    private final ConversationAppService conversationAppService;
    private final ConversationAttachmentAppService conversationAttachmentAppService;
    private final AgentCatalogAppService agentCatalogAppService;
    private final MessageRunAppService messageRunAppService;
    private final ApprovalAppService approvalAppService;
    private final ModelConfigAppService modelConfigAppService;
    private final SystemAppService systemAppService;
    private final CronJobApplicationService cronJobApplicationService;

    public AgentController(ConversationAppService conversationAppService,
                           ConversationAttachmentAppService conversationAttachmentAppService,
                           AgentCatalogAppService agentCatalogAppService,
                           MessageRunAppService messageRunAppService,
                           ApprovalAppService approvalAppService,
                           ModelConfigAppService modelConfigAppService,
                           SystemAppService systemAppService,
                           CronJobApplicationService cronJobApplicationService) {
        this.conversationAppService = conversationAppService;
        this.conversationAttachmentAppService = conversationAttachmentAppService;
        this.agentCatalogAppService = agentCatalogAppService;
        this.messageRunAppService = messageRunAppService;
        this.approvalAppService = approvalAppService;
        this.modelConfigAppService = modelConfigAppService;
        this.systemAppService = systemAppService;
        this.cronJobApplicationService = cronJobApplicationService;
    }

    @PostMapping("/conversations")
    public CreateConversationResponse createConversation(@RequestBody(required = false) CreateConversationRequest request) {
        String conversationUid = conversationAppService.createConversation(
                request == null ? "" : request.agentGroupUid(),
                request == null ? "" : request.agentUid()
        );
        log.info("[AgentAPI] createConversation conversationUid={}", conversationUid);
        return new CreateConversationResponse(conversationUid);
    }

    @GetMapping("/conversations")
    public java.util.List<ConversationSummaryResponse> listConversations() {
        log.info("[AgentAPI] listConversations");
        return ApiDtoMapper.toConversationSummaries(conversationAppService.listConversations());
    }

    @DeleteMapping("/conversations/{conversationUid}")
    public SimpleResponse deleteConversation(@PathVariable String conversationUid) {
        log.info("[AgentAPI] deleteConversation conversationUid={}", conversationUid);
        conversationAppService.deleteConversation(conversationUid);
        return new SimpleResponse("deleted");
    }

    @PatchMapping("/conversations/{conversationUid}/title")
    public SimpleResponse updateConversationTitle(@PathVariable String conversationUid,
                                                  @Valid @RequestBody UpdateConversationTitleRequest request) {
        log.info("[AgentAPI] updateConversationTitle conversationUid={} title={}", conversationUid, request.title());
        conversationAppService.updateConversationTitle(conversationUid, request.title());
        return new SimpleResponse("updated");
    }

    @GetMapping("/agent-groups")
    public java.util.List<AgentCatalogGroupResponse> listAgentGroups() {
        log.info("[AgentAPI] listAgentGroups");
        return ApiDtoMapper.toAgentCatalogGroups(agentCatalogAppService.listAgentGroups());
    }

    @GetMapping("/agents/{agentUid}/skills")
    public java.util.List<AgentSkillResponse> listAgentSkills(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentSkills agentUid={}", agentUid);
        return ApiDtoMapper.toAgentSkills(agentCatalogAppService.listAgentSkills(agentUid));
    }

    @PostMapping("/agents")
    public AgentCatalogAgentResponse createAgent(@Valid @RequestBody CreateAgentRequest request) {
        log.info("[AgentAPI] createAgent agentName={} displayName={}", request.agentName(), request.displayName());
        return ApiDtoMapper.toAgentCatalogAgent(agentCatalogAppService.createAgent(ApiDtoMapper.toCommand(request)));
    }

    @PatchMapping("/agents/{agentUid}/basic")
    public AgentCatalogAgentResponse updateAgentBasicInfo(@PathVariable String agentUid,
                                                          @Valid @RequestBody UpdateAgentBasicInfoRequest request) {
        log.info("[AgentAPI] updateAgentBasicInfo agentUid={} displayName={} avatar={} avatarColor={} modelProvider={} modelName={}",
                agentUid, request.displayName(), request.avatar(), request.avatarColor(), request.modelProvider(), request.modelName());
        return ApiDtoMapper.toAgentCatalogAgent(agentCatalogAppService.updateAgentBasicInfo(agentUid, ApiDtoMapper.toCommand(request)));
    }

    @GetMapping("/agents/{agentUid}/tools")
    public java.util.List<AgentToolResponse> listAgentTools(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentTools agentUid={}", agentUid);
        return ApiDtoMapper.toAgentTools(agentCatalogAppService.listAgentTools(agentUid));
    }

    @GetMapping("/agents/{agentUid}/tips")
    public java.util.List<AgentTipResponse> listAgentTips(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentTips agentUid={}", agentUid);
        return ApiDtoMapper.toAgentTips(agentCatalogAppService.listAgentTips(agentUid));
    }

    @PostMapping("/agents/{agentUid}/tips")
    public AgentTipResponse createAgentTip(@PathVariable String agentUid,
                                           @RequestBody(required = false) CreateAgentTipRequest request) {
        log.info("[AgentAPI] createAgentTip agentUid={} sourceConversationUid={} sourceMessageUid={} generateBestPractice={}",
                agentUid,
                request == null ? null : request.sourceConversationUid(),
                request == null ? null : request.sourceMessageUid(),
                request == null ? null : request.generateBestPractice());
        return ApiDtoMapper.toAgentTip(agentCatalogAppService.createAgentTip(agentUid, ApiDtoMapper.toCommand(request)));
    }

    @DeleteMapping("/agents/{agentUid}/tips/{tipUid}")
    public SimpleResponse deleteAgentTip(@PathVariable String agentUid, @PathVariable String tipUid) {
        log.info("[AgentAPI] deleteAgentTip agentUid={} tipUid={}", agentUid, tipUid);
        agentCatalogAppService.deleteAgentTip(agentUid, tipUid);
        return new SimpleResponse("deleted");
    }

    @GetMapping("/system/config")
    public SystemConfigResponse getSystemConfig() {
        log.info("[AgentAPI] getSystemConfig");
        return ApiDtoMapper.toSystemConfig(systemAppService.getSystemConfig());
    }

    @GetMapping("/system/channels")
    public ChannelConfigResponse getChannelConfig() {
        log.info("[AgentAPI] getChannelConfig");
        return ApiDtoMapper.toChannelConfig(systemAppService.getChannelConfig());
    }

    @GetMapping("/system/models")
    public ModelConfigResponse getModelConfig() {
        log.info("[AgentAPI] getModelConfig");
        return ApiDtoMapper.toModelConfig(modelConfigAppService.getModelConfig());
    }

    @GetMapping("/system/models/available")
    public ModelConfigResponse getAvailableModelConfig() {
        log.info("[AgentAPI] getAvailableModelConfig");
        return ApiDtoMapper.toModelConfig(modelConfigAppService.getAvailableModelConfig());
    }

    @PutMapping("/system/channels")
    public ChannelConfigResponse updateChannelConfig(@RequestBody(required = false) UpdateChannelConfigRequest request) {
        log.info("[AgentAPI] updateChannelConfig");
        return ApiDtoMapper.toChannelConfig(systemAppService.updateChannelConfig(ApiDtoMapper.toChannelConfig(request)));
    }

    @PutMapping("/system/models")
    public ModelConfigResponse updateModelConfig(@RequestBody(required = false) UpdateModelConfigRequest request) {
        log.info("[AgentAPI] updateModelConfig");
        return ApiDtoMapper.toModelConfig(modelConfigAppService.updateModelConfig(ApiDtoMapper.toModelConfig(request)));
    }

    @PostMapping("/system/models/providers/{providerId}/load-local")
    public ModelConfigResponse loadLocalModels(@PathVariable String providerId) {
        log.info("[AgentAPI] loadLocalModels providerId={}", providerId);
        return ApiDtoMapper.toModelConfig(modelConfigAppService.loadLocalModels(providerId));
    }

    @PatchMapping("/agents/{agentUid}/skills/{skillKey}")
    public AgentSkillResponse updateAgentSkillStatus(@PathVariable String agentUid,
                                                     @PathVariable String skillKey,
                                                     @Valid @RequestBody UpdateAgentSkillStatusRequest request) {
        log.info("[AgentAPI] updateAgentSkillStatus agentUid={} skillKey={} enabled={}",
                agentUid, skillKey, request.enabled());
        return ApiDtoMapper.toAgentSkill(agentCatalogAppService.updateAgentSkillStatus(agentUid, skillKey, request.enabled()));
    }

    @PostMapping("/agents/{agentUid}/skills/import-url")
    public AgentSkillResponse importSkillFromUrl(@PathVariable String agentUid,
                                                 @RequestBody(required = false) ImportSkillFromUrlRequest request) {
        log.info("[AgentAPI] importSkillFromUrl agentUid={} url={} attachToAgent={}",
                agentUid,
                request == null ? null : request.url(),
                request != null && Boolean.TRUE.equals(request.attachToAgent()));
        return ApiDtoMapper.toAgentSkill(agentCatalogAppService.importSkillFromUrl(agentUid, ApiDtoMapper.toCommand(request)));
    }

    @PostMapping(value = "/agents/{agentUid}/skills/import-archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AgentSkillResponse importSkillArchive(@PathVariable String agentUid,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestParam(name = "attachToAgent", defaultValue = "true") boolean attachToAgent) {
        log.info("[AgentAPI] importSkillArchive agentUid={} fileName={} attachToAgent={}",
                agentUid,
                file == null ? null : file.getOriginalFilename(),
                attachToAgent);
        return ApiDtoMapper.toAgentSkill(agentCatalogAppService.importSkillArchive(agentUid, file, attachToAgent));
    }

    @PostMapping("/agents/{agentUid}/skills/create")
    public AgentSkillResponse createSkill(@PathVariable String agentUid,
                                          @RequestBody(required = false) CreateSkillRequest request) {
        log.info("[AgentAPI] createSkill agentUid={} skillKey={} attachToAgent={}",
                agentUid,
                request == null ? null : request.skillKey(),
                request != null && Boolean.TRUE.equals(request.attachToAgent()));
        return ApiDtoMapper.toAgentSkill(agentCatalogAppService.createSkill(agentUid, ApiDtoMapper.toCommand(request)));
    }

    @PatchMapping("/agents/{agentUid}/tools/{toolKey}")
    public AgentToolResponse updateAgentToolStatus(@PathVariable String agentUid,
                                                   @PathVariable String toolKey,
                                                   @Valid @RequestBody UpdateAgentToolStatusRequest request) {
        log.info("[AgentAPI] updateAgentToolStatus agentUid={} toolKey={} enabled={}",
                agentUid, toolKey, request.enabled());
        return ApiDtoMapper.toAgentTool(agentCatalogAppService.updateAgentToolStatus(agentUid, toolKey, request.enabled()));
    }

    @GetMapping("/conversations/{conversationUid}/messages")
    public java.util.List<ConversationMessageResponse> listMessages(@PathVariable String conversationUid) {
        log.info("[AgentAPI] listMessages conversationUid={}", conversationUid);
        return ApiDtoMapper.toConversationMessages(conversationAppService.listMessages(conversationUid));
    }

    @GetMapping("/conversations/{conversationUid}/message-runs")
    public java.util.List<ConversationMessageRunResponse> listMessageRuns(@PathVariable String conversationUid) {
        log.info("[AgentAPI] listMessageRuns conversationUid={}", conversationUid);
        return ApiDtoMapper.toMessageRuns(conversationAppService.listMessageRuns(conversationUid));
    }

    @PostMapping("/conversations/{conversationUid}/messages")
    public MessageResponse submitMessage(@PathVariable String conversationUid, @Valid @RequestBody MessageRequest request) {
        log.info("[AgentAPI] submitMessage conversationUid={} messageLength={}", conversationUid, request.message().length());
        String messageUid = messageRunAppService.submitMessage(
                conversationUid,
                request.message(),
                request.fileUrls() == null ? List.of() : request.fileUrls(),
                request.modelProvider(),
                request.modelName()
        );
        var message = messageRunAppService.getMessage(messageUid);
        int maxRounds = messageRunAppService.maxLoopRounds();
        log.info("[AgentAPI] submitMessage accepted conversationUid={} messageUid={} round={}/{}",
                conversationUid, messageUid, 1, maxRounds);
        return new MessageResponse(messageUid, "accepted", 1, maxRounds, null);
    }

    @PostMapping(value = "/conversations/{conversationUid}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadFilesResponse uploadConversationFiles(@PathVariable String conversationUid,
                                                       @RequestParam("files") List<MultipartFile> files,
                                                       @RequestParam("modelProvider") String modelProvider,
                                                       @RequestParam("modelName") String modelName) {
        log.info("[AgentAPI] uploadConversationFiles conversationUid={} modelProvider={} modelName={} count={}",
                conversationUid, modelProvider, modelName, files == null ? 0 : files.size());
        return new UploadFilesResponse(
                conversationAttachmentAppService.uploadFiles(conversationUid, modelProvider, modelName, files).stream()
                        .map(ApiDtoMapper::toConversationAttachment)
                        .toList()
        );
    }

    @GetMapping("/conversations/{conversationUid}/uploads/{uploadUid}/content")
    public ResponseEntity<Resource> readConversationUpload(@PathVariable String conversationUid,
                                                           @PathVariable String uploadUid) {
        var attachment = conversationAttachmentAppService.requireAttachment(conversationUid, uploadUid);
        Path filePath = Path.of(attachment.getFilePath()).toAbsolutePath().normalize();
        FileSystemResource resource = new FileSystemResource(filePath);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(attachment.getContentType());
        } catch (Exception ignored) {
            // fallback
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(attachment.getOriginalName()).build().toString())
                .contentType(mediaType)
                .body(resource);
    }

    @PostMapping("/conversations/{conversationUid}/approvals/{stepUid}")
    public SimpleResponse approveStep(@PathVariable String conversationUid, @PathVariable String stepUid) {
        log.info("[AgentAPI] approveStep conversationUid={} stepUid={}", conversationUid, stepUid);
        approvalAppService.approveStep(conversationUid, stepUid);
        return new SimpleResponse("approved");
    }

    @PostMapping("/conversations/{conversationUid}/approvals/{stepUid}/reject")
    public SimpleResponse rejectStep(@PathVariable String conversationUid, @PathVariable String stepUid) {
        log.info("[AgentAPI] rejectStep conversationUid={} stepUid={}", conversationUid, stepUid);
        approvalAppService.rejectStep(conversationUid, stepUid);
        return new SimpleResponse("rejected");
    }

    @PostMapping("/conversations/{conversationUid}/cancel")
    public SimpleResponse cancelMessage(@PathVariable String conversationUid) {
        log.info("[AgentAPI] cancelMessage conversationUid={}", conversationUid);
        messageRunAppService.cancelLatestMessage(conversationUid);
        return new SimpleResponse("canceled");
    }

    @PostMapping("/files/open")
    public SimpleResponse openFile(@Valid @RequestBody OpenFileRequest request) {
        log.info("[AgentAPI] openFile path={}", request.path());
        systemAppService.openFile(request.path());
        return new SimpleResponse("opened");
    }

    @GetMapping("/cron-jobs")
    public java.util.List<CronJobResponse> listCronJobs() {
        log.info("[AgentAPI] listCronJobs");
        return ApiDtoMapper.toCronJobs(cronJobApplicationService.listCronJobs());
    }

    @PostMapping("/cron-jobs")
    public CronJobResponse createCronJob(@RequestBody(required = false) CreateCronJobRequest request) {
        log.info("[AgentAPI] createCronJob agentUid={} title={} expression={} timezone={} status={}",
                request == null ? null : request.agentUid(),
                request == null ? null : request.title(),
                request == null ? null : request.expression(),
                request == null ? null : request.timezone(),
                request == null ? null : request.status());
        return ApiDtoMapper.toCronJob(cronJobApplicationService.createCronJob(ApiDtoMapper.toCommand(request)));
    }

    @GetMapping("/cron-jobs/{jobUid}")
    public CronJobResponse getCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] getCronJob jobUid={}", jobUid);
        return ApiDtoMapper.toCronJob(cronJobApplicationService.getCronJob(jobUid));
    }

    @GetMapping("/cron-jobs/{jobUid}/report")
    public CronJobReportResponse getCronJobReport(@PathVariable String jobUid) {
        log.info("[AgentAPI] getCronJobReport jobUid={}", jobUid);
        return ApiDtoMapper.toCronJobReport(cronJobApplicationService.getLatestReport(jobUid));
    }

    @GetMapping("/cron-jobs/{jobUid}/results")
    public java.util.List<CronJobExecutionResultResponse> listCronJobResults(@PathVariable String jobUid) {
        log.info("[AgentAPI] listCronJobResults jobUid={}", jobUid);
        return ApiDtoMapper.toCronJobExecutionResults(cronJobApplicationService.listRecentResults(jobUid, 20));
    }

    @PostMapping("/cron-jobs/{jobUid}/run")
    public CronJobResponse runCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] runCronJob jobUid={}", jobUid);
        return ApiDtoMapper.toCronJob(cronJobApplicationService.runCronJob(jobUid));
    }

    @PatchMapping("/cron-jobs/{jobUid}")
    public CronJobResponse updateCronJob(@PathVariable String jobUid,
                                         @RequestBody(required = false) UpdateCronJobRequest request) {
        log.info("[AgentAPI] updateCronJob jobUid={} title={} expression={} timezone={} taskContent={} status={}",
                jobUid,
                request == null ? null : request.title(),
                request == null ? null : request.expression(),
                request == null ? null : request.timezone(),
                request == null ? null : request.taskContent(),
                request == null ? null : request.status());
        return ApiDtoMapper.toCronJob(cronJobApplicationService.updateCronJob(jobUid, ApiDtoMapper.toCommand(request)));
    }

    @PostMapping("/cron-jobs/{jobUid}/pause")
    public CronJobResponse pauseCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] pauseCronJob jobUid={}", jobUid);
        return ApiDtoMapper.toCronJob(cronJobApplicationService.pauseCronJob(jobUid));
    }

    @PostMapping("/cron-jobs/{jobUid}/resume")
    public CronJobResponse resumeCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] resumeCronJob jobUid={}", jobUid);
        return ApiDtoMapper.toCronJob(cronJobApplicationService.resumeCronJob(jobUid));
    }

    @DeleteMapping("/cron-jobs/{jobUid}")
    public SimpleResponse deleteCronJob(@PathVariable String jobUid) {
        log.info("[AgentAPI] deleteCronJob jobUid={}", jobUid);
        cronJobApplicationService.deleteCronJob(jobUid);
        return new SimpleResponse("deleted");
    }

    @GetMapping("/cron-jobs/{jobUid}/subscriptions")
    public java.util.List<CronSubscriptionResponse> listCronSubscriptions(@PathVariable String jobUid) {
        log.info("[AgentAPI] listCronSubscriptions jobUid={}", jobUid);
        return ApiDtoMapper.toCronSubscriptions(cronJobApplicationService.listCronSubscriptions(jobUid));
    }

    @PutMapping("/cron-jobs/{jobUid}/subscriptions")
    public java.util.List<CronSubscriptionResponse> updateCronSubscriptions(@PathVariable String jobUid,
                                                                            @RequestBody(required = false) UpdateCronSubscriptionsRequest request) {
        int requestedCount = request == null || request.subscriptions() == null ? 0 : request.subscriptions().size();
        log.info("[AgentAPI] updateCronSubscriptions jobUid={} requestedCount={}", jobUid, requestedCount);
        return ApiDtoMapper.toCronSubscriptions(
                cronJobApplicationService.updateCronSubscriptions(jobUid, ApiDtoMapper.toCronSubscriptions(request))
        );
    }

    @PostMapping("/cron-jobs/batch-delete")
    public BatchDeleteCronJobsResponse batchDeleteCronJobs(@RequestBody(required = false) BatchDeleteCronJobsRequest request) {
        int requestedCount = request == null || request.jobUids() == null ? 0 : request.jobUids().size();
        log.info("[AgentAPI] batchDeleteCronJobs requestedCount={}", requestedCount);
        return ApiDtoMapper.toBatchDeleteResult(cronJobApplicationService.batchDeleteCronJobs(
                request == null ? java.util.List.of() : request.jobUids()
        ));
    }

    @GetMapping(value = "/conversations/{conversationUid}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String conversationUid) {
        log.info("[AgentAPI] subscribeEvents conversationUid={}", conversationUid);
        return messageRunAppService.subscribe(conversationUid);
    }
}
