package ai.nomoclaw.bot.api;

import ai.nomoclaw.bot.mcp.McpApplicationService;
import ai.nomoclaw.bot.orchestrator.*;
import ai.nomoclaw.bot.scheduler.CronChannelTargetDirectoryService;
import ai.nomoclaw.bot.scheduler.CronJobApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    private final PermissionAppService permissionAppService;
    private final CronJobApplicationService cronJobApplicationService;
    private final CronChannelTargetDirectoryService cronChannelTargetDirectoryService;
    private final McpApplicationService mcpApplicationService;

    public AgentController(ConversationAppService conversationAppService,
                           ConversationAttachmentAppService conversationAttachmentAppService,
                           AgentCatalogAppService agentCatalogAppService,
                           MessageRunAppService messageRunAppService,
                           ApprovalAppService approvalAppService,
                           ModelConfigAppService modelConfigAppService,
                           SystemAppService systemAppService,
                           PermissionAppService permissionAppService,
                           CronJobApplicationService cronJobApplicationService,
                           CronChannelTargetDirectoryService cronChannelTargetDirectoryService,
                           McpApplicationService mcpApplicationService) {
        this.conversationAppService = conversationAppService;
        this.conversationAttachmentAppService = conversationAttachmentAppService;
        this.agentCatalogAppService = agentCatalogAppService;
        this.messageRunAppService = messageRunAppService;
        this.approvalAppService = approvalAppService;
        this.modelConfigAppService = modelConfigAppService;
        this.systemAppService = systemAppService;
        this.permissionAppService = permissionAppService;
        this.cronJobApplicationService = cronJobApplicationService;
        this.cronChannelTargetDirectoryService = cronChannelTargetDirectoryService;
        this.mcpApplicationService = mcpApplicationService;
    }

    @GetMapping("/mcp/servers")
    public List<McpServerResponse> listMcpServers() {
        log.info("[AgentAPI] listMcpServers");
        return ApiDtoMapper.toMcpServers(mcpApplicationService.listServers());
    }

    @PostMapping("/mcp/servers")
    public McpServerResponse createMcpServer(@Valid @RequestBody SaveMcpServerRequest request) {
        log.info("[AgentAPI] createMcpServer serverName={} transport={}", request.serverName(), request.transport());
        return ApiDtoMapper.toMcpServer(mcpApplicationService.createServer(ApiDtoMapper.toCommand(request)));
    }

    @PutMapping("/mcp/servers/{serverUid}")
    public McpServerResponse updateMcpServer(@PathVariable String serverUid,
                                             @Valid @RequestBody SaveMcpServerRequest request) {
        log.info("[AgentAPI] updateMcpServer serverUid={} serverName={} transport={}", serverUid, request.serverName(), request.transport());
        return ApiDtoMapper.toMcpServer(mcpApplicationService.updateServer(serverUid, ApiDtoMapper.toCommand(request)));
    }

    @PatchMapping("/mcp/servers/{serverUid}/status")
    public McpServerResponse updateMcpServerStatus(@PathVariable String serverUid,
                                                   @Valid @RequestBody UpdateMcpServerStatusRequest request) {
        log.info("[AgentAPI] updateMcpServerStatus serverUid={} enabled={}", serverUid, request.enabled());
        return ApiDtoMapper.toMcpServer(mcpApplicationService.updateServerStatus(serverUid, request.enabled()));
    }

    @DeleteMapping("/mcp/servers/{serverUid}")
    public SimpleResponse deleteMcpServer(@PathVariable String serverUid) {
        log.info("[AgentAPI] deleteMcpServer serverUid={}", serverUid);
        mcpApplicationService.deleteServer(serverUid);
        return new SimpleResponse("deleted");
    }

    @PostMapping("/mcp/servers/{serverUid}/test")
    public McpServerResponse testMcpServer(@PathVariable String serverUid) {
        log.info("[AgentAPI] testMcpServer serverUid={}", serverUid);
        return ApiDtoMapper.toMcpServer(mcpApplicationService.testServer(serverUid));
    }

    @PostMapping("/mcp/servers/{serverUid}/refresh-tools")
    public List<McpToolResponse> refreshMcpTools(@PathVariable String serverUid) {
        log.info("[AgentAPI] refreshMcpTools serverUid={}", serverUid);
        return ApiDtoMapper.toMcpTools(mcpApplicationService.refreshTools(serverUid));
    }

    @GetMapping("/mcp/servers/{serverUid}/tools")
    public List<McpToolResponse> listMcpTools(@PathVariable String serverUid) {
        log.info("[AgentAPI] listMcpTools serverUid={}", serverUid);
        return ApiDtoMapper.toMcpTools(mcpApplicationService.listTools(serverUid));
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
    public List<ConversationSummaryResponse> listConversations() {
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

    @PatchMapping("/conversations/{conversationUid}/pin")
    public SimpleResponse updateConversationPinned(@PathVariable String conversationUid,
                                                   @Valid @RequestBody UpdateConversationPinnedRequest request) {
        log.info("[AgentAPI] updateConversationPinned conversationUid={} pinned={}", conversationUid, request.pinned());
        conversationAppService.updateConversationPinned(conversationUid, Boolean.TRUE.equals(request.pinned()));
        return new SimpleResponse("updated");
    }

    @GetMapping("/agent-groups")
    public List<AgentCatalogGroupResponse> listAgentGroups() {
        log.info("[AgentAPI] listAgentGroups");
        return ApiDtoMapper.toAgentCatalogGroups(agentCatalogAppService.listAgentGroups());
    }

    @GetMapping("/skills")
    public List<GlobalSkillResponse> listSkills() {
        log.info("[AgentAPI] listSkills");
        return ApiDtoMapper.toGlobalSkills(agentCatalogAppService.listSkills());
    }

    @GetMapping("/skills/{skillKey}/bindings")
    public SkillBindingsResponse getSkillBindings(@PathVariable String skillKey) {
        log.info("[AgentAPI] getSkillBindings skillKey={}", skillKey);
        return ApiDtoMapper.toSkillBindings(agentCatalogAppService.getSkillBindings(skillKey));
    }

    @GetMapping("/agents/{agentUid}/skills")
    public List<AgentSkillResponse> listAgentSkills(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentSkills agentUid={}", agentUid);
        return ApiDtoMapper.toAgentSkills(agentCatalogAppService.listAgentSkills(agentUid));
    }

    @PostMapping("/agents")
    public AgentCatalogAgentResponse createAgent(@Valid @RequestBody CreateAgentRequest request) {
        log.info("[AgentAPI] createAgent agentName={} displayName={}", request.agentName(), request.displayName());
        return ApiDtoMapper.toAgentCatalogAgent(agentCatalogAppService.createAgent(ApiDtoMapper.toCommand(request)));
    }

    @DeleteMapping("/agents/{agentUid}")
    public SimpleResponse deleteAgent(@PathVariable String agentUid) {
        log.info("[AgentAPI] deleteAgent agentUid={}", agentUid);
        agentCatalogAppService.deleteAgent(agentUid);
        return new SimpleResponse("deleted");
    }

    @PatchMapping("/agents/{agentUid}/basic")
    public AgentCatalogAgentResponse updateAgentBasicInfo(@PathVariable String agentUid,
                                                          @Valid @RequestBody UpdateAgentBasicInfoRequest request) {
        log.info("[AgentAPI] updateAgentBasicInfo agentUid={} displayName={} avatar={} avatarColor={} modelProvider={} modelName={}",
                agentUid, request.displayName(), request.avatar(), request.avatarColor(), request.modelProvider(), request.modelName());
        return ApiDtoMapper.toAgentCatalogAgent(agentCatalogAppService.updateAgentBasicInfo(agentUid, ApiDtoMapper.toCommand(request)));
    }

    @GetMapping("/agents/{agentUid}/tools")
    public List<AgentToolResponse> listAgentTools(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentTools agentUid={}", agentUid);
        return ApiDtoMapper.toAgentTools(agentCatalogAppService.listAgentTools(agentUid));
    }

    @GetMapping("/agents/{agentUid}/mcp-tools")
    public List<AgentMcpToolResponse> listAgentMcpTools(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentMcpTools agentUid={}", agentUid);
        return ApiDtoMapper.toAgentMcpTools(agentCatalogAppService.listAgentMcpTools(agentUid));
    }

    @GetMapping("/agents/{agentUid}/tips")
    public List<AgentTipResponse> listAgentTips(@PathVariable String agentUid) {
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

    @PutMapping("/agents/{agentUid}/tips/{tipUid}")
    public AgentTipResponse updateAgentTip(@PathVariable String agentUid,
                                           @PathVariable String tipUid,
                                           @RequestBody(required = false) UpdateAgentTipRequest request) {
        log.info("[AgentAPI] updateAgentTip agentUid={} tipUid={}", agentUid, tipUid);
        return ApiDtoMapper.toAgentTip(agentCatalogAppService.updateAgentTip(agentUid, tipUid, ApiDtoMapper.toCommand(request)));
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

    @GetMapping("/system/channels/targets/search")
    public ChannelTargetSearchResponse searchChannelTargets(@RequestParam String channel,
                                                            @RequestParam(required = false, defaultValue = "") String keyword,
                                                            @RequestParam(required = false, defaultValue = "") String botId,
                                                            @RequestParam(required = false, defaultValue = "20") int limit) {
        log.info("[AgentAPI] searchChannelTargets channel={} keyword={} botId={} limit={}", channel, keyword, botId, limit);
        return ApiDtoMapper.toChannelTargetSearch(cronChannelTargetDirectoryService.search(channel, keyword, botId, limit));
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

    @GetMapping("/system/models/catalog/status")
    public ModelCatalogStatusResponse getModelCatalogStatus() {
        log.info("[AgentAPI] getModelCatalogStatus");
        return ApiDtoMapper.toModelCatalogStatus(modelConfigAppService.getCatalogStatus());
    }

    @PostMapping("/system/models/catalog/refresh")
    public ModelCatalogStatusResponse refreshModelCatalog() {
        log.info("[AgentAPI] refreshModelCatalog");
        return ApiDtoMapper.toModelCatalogStatus(modelConfigAppService.refreshModelCatalog());
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

    @PostMapping("/system/models/providers/test")
    public ModelProviderTestResponse testModelProvider(@RequestBody(required = false) TestModelProviderRequest request) {
        String providerId = request == null ? "" : request.providerId();
        log.info("[AgentAPI] testModelProvider providerId={}", providerId);
        ModelConfigAppService.ProbeResult result = modelConfigAppService.testProviderConnection(
                providerId,
                request == null ? "" : request.baseUrl(),
                request == null ? "" : request.apiKey()
        );
        return new ModelProviderTestResponse(result.success(), result.message());
    }

    @PostMapping("/system/models/codex/login")
    public ModelProviderTestResponse startCodexLogin() {
        log.info("[AgentAPI] startCodexLogin");
        ModelConfigAppService.ProbeResult result = modelConfigAppService.startCodexLogin();
        return new ModelProviderTestResponse(result.success(), result.message());
    }

    @PatchMapping("/agents/{agentUid}/skills/{skillKey}")
    public AgentSkillResponse updateAgentSkillStatus(@PathVariable String agentUid,
                                                     @PathVariable String skillKey,
                                                     @Valid @RequestBody UpdateAgentSkillStatusRequest request) {
        log.info("[AgentAPI] updateAgentSkillStatus agentUid={} skillKey={} enabled={}",
                agentUid, skillKey, request.enabled());
        return ApiDtoMapper.toAgentSkill(agentCatalogAppService.updateAgentSkillStatus(agentUid, skillKey, request.enabled()));
    }

    @PatchMapping("/skills/{skillKey}")
    public GlobalSkillResponse updateSkillStatus(@PathVariable String skillKey,
                                                 @Valid @RequestBody UpdateSkillStatusRequest request) {
        log.info("[AgentAPI] updateSkillStatus skillKey={} enabled={}", skillKey, request.enabled());
        return ApiDtoMapper.toGlobalSkill(agentCatalogAppService.updateSkillStatus(skillKey, request.enabled()));
    }

    @PutMapping("/skills/{skillKey}/bindings")
    public SkillBindingsResponse updateSkillBindings(@PathVariable String skillKey,
                                                     @Valid @RequestBody UpdateSkillBindingsRequest request) {
        log.info("[AgentAPI] updateSkillBindings skillKey={} enabled={} agents={}",
                skillKey,
                request.enabled(),
                request.agentBindings() == null ? 0 : request.agentBindings().size());
        return ApiDtoMapper.toSkillBindings(agentCatalogAppService.updateSkillBindings(skillKey, ApiDtoMapper.toCommand(request)));
    }

    @DeleteMapping("/skills/{skillKey}")
    public SimpleResponse deleteSkill(@PathVariable String skillKey) {
        log.info("[AgentAPI] deleteSkill skillKey={}", skillKey);
        agentCatalogAppService.deleteSkill(skillKey);
        return new SimpleResponse("deleted");
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

    @PatchMapping("/agents/{agentUid}/mcp-tools/{toolKey}")
    public AgentMcpToolResponse updateAgentMcpToolStatus(@PathVariable String agentUid,
                                                         @PathVariable String toolKey,
                                                         @Valid @RequestBody UpdateAgentToolStatusRequest request) {
        log.info("[AgentAPI] updateAgentMcpToolStatus agentUid={} toolKey={} enabled={}",
                agentUid, toolKey, request.enabled());
        return ApiDtoMapper.toAgentMcpTool(agentCatalogAppService.updateAgentMcpToolStatus(agentUid, toolKey, request.enabled()));
    }

    @GetMapping("/agents/{agentUid}/docs")
    public List<AgentDocResponse> listAgentDocs(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentDocs agentUid={}", agentUid);
        return ApiDtoMapper.toAgentDocs(agentCatalogAppService.listAgentDocs(agentUid));
    }

    @PutMapping("/agents/{agentUid}/docs/{docKey}")
    public AgentDocResponse updateAgentDoc(@PathVariable String agentUid,
                                           @PathVariable String docKey,
                                           @RequestBody(required = false) UpdateAgentDocRequest request) {
        log.info("[AgentAPI] updateAgentDoc agentUid={} docKey={}", agentUid, docKey);
        return ApiDtoMapper.toAgentDoc(agentCatalogAppService.updateAgentDoc(
                agentUid,
                docKey,
                request == null ? "" : request.content()
        ));
    }

    @GetMapping("/conversations/{conversationUid}/messages")
    public List<ConversationMessageResponse> listMessages(@PathVariable String conversationUid) {
        log.info("[AgentAPI] listMessages conversationUid={}", conversationUid);
        return ApiDtoMapper.toConversationMessages(conversationAppService.listMessages(conversationUid));
    }

    @GetMapping("/conversations/{conversationUid}/message-runs")
    public List<ConversationMessageRunResponse> listMessageRuns(@PathVariable String conversationUid) {
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
                request.modelName(),
                request.approvalMode()
        );
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

    @PostMapping("/conversations/{conversationUid}/approvals/{stepUid}/decision")
    public ApprovalDecisionResponse decideStep(@PathVariable String conversationUid,
                                               @PathVariable String stepUid,
                                               @RequestBody(required = false) ApprovalDecisionRequest request) {
        String action = request == null ? "allow" : request.action();
        String scope = request == null ? "once" : request.scope();
        String note = request == null ? "" : request.note();
        log.info("[AgentAPI] decideStep conversationUid={} stepUid={} action={} scope={}",
                conversationUid, stepUid, action, scope);
        var decision = approvalAppService.decideStep(conversationUid, stepUid, action, scope, note);
        return new ApprovalDecisionResponse(decision.status(), decision.appliedScope(), decision.persisted(), decision.matchedRuleId());
    }

    @GetMapping("/permissions/effective")
    public PermissionRulesResponse getEffectivePermissions(@RequestParam(required = false, defaultValue = "") String conversationUid,
                                                           @RequestParam(required = false, defaultValue = "") String agentUid) {
        log.info("[AgentAPI] getEffectivePermissions conversationUid={} agentUid={}", conversationUid, agentUid);
        return permissionAppService.getEffectiveRules(conversationUid, agentUid);
    }

    @PutMapping("/permissions/agent-settings/{agentUid}")
    public PermissionRulesResponse updateAgentPermissions(@PathVariable String agentUid,
                                                          @RequestBody(required = false) UpdatePermissionRulesRequest request) {
        log.info("[AgentAPI] updateAgentPermissions agentUid={} rules={}", agentUid,
                request == null || request.rules() == null ? 0 : request.rules().size());
        return permissionAppService.updateAgentRules(agentUid, request == null ? new UpdatePermissionRulesRequest(List.of()) : request);
    }

    @PutMapping("/permissions/user-settings")
    public PermissionRulesResponse updateUserPermissions(@RequestParam(required = false, defaultValue = "") String agentUid,
                                                         @RequestBody(required = false) UpdatePermissionRulesRequest request) {
        log.info("[AgentAPI] updateUserPermissions agentUid={} rules={}", agentUid,
                request == null || request.rules() == null ? 0 : request.rules().size());
        return permissionAppService.updateUserRules(agentUid, request == null ? new UpdatePermissionRulesRequest(List.of()) : request);
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
    public List<CronJobResponse> listCronJobs() {
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
    public List<CronJobExecutionResultResponse> listCronJobResults(@PathVariable String jobUid) {
        log.info("[AgentAPI] listCronJobResults jobUid={}", jobUid);
        return ApiDtoMapper.toCronJobExecutionResults(cronJobApplicationService.listRecentResults(jobUid, 20));
    }

    @GetMapping("/cron-jobs/results/recent")
    public List<CronJobExecutionResultResponse> listRecentCronJobResults(@RequestParam(required = false, defaultValue = "20") int limit) {
        log.info("[AgentAPI] listRecentCronJobResults limit={}", limit);
        return ApiDtoMapper.toCronJobExecutionResults(cronJobApplicationService.listGlobalRecentResults(limit));
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
        return ApiDtoMapper.toCronJobExecutionHistoryPage(
                cronJobApplicationService.listExecutionHistory(agentUid, status, startDate, endDate, page, pageSize)
        );
    }

    @GetMapping("/cron-jobs/executions/{executionUid}")
    public CronExecutionDetailResponse getCronExecutionDetail(@PathVariable String executionUid) {
        log.info("[AgentAPI] getCronExecutionDetail executionUid={}", executionUid);
        return ApiDtoMapper.toCronExecutionDetail(cronJobApplicationService.getExecutionDetail(executionUid));
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
        return ApiDtoMapper.toCronJob(cronJobApplicationService.runCronJob(jobUid));
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
    public List<CronSubscriptionResponse> listCronSubscriptions(@PathVariable String jobUid) {
        log.info("[AgentAPI] listCronSubscriptions jobUid={}", jobUid);
        return ApiDtoMapper.toCronSubscriptions(cronJobApplicationService.listCronSubscriptions(jobUid));
    }

    @PutMapping("/cron-jobs/{jobUid}/subscriptions")
    public List<CronSubscriptionResponse> updateCronSubscriptions(@PathVariable String jobUid,
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
                request == null ? List.of() : request.jobUids()
        ));
    }

    @GetMapping(value = "/conversations/{conversationUid}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String conversationUid) {
        log.info("[AgentAPI] subscribeEvents conversationUid={}", conversationUid);
        return messageRunAppService.subscribe(conversationUid);
    }
}
