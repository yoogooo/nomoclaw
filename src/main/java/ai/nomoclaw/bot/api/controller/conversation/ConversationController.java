package ai.nomoclaw.bot.api.controller.conversation;

import ai.nomoclaw.bot.api.dto.common.response.SimpleResponse;
import ai.nomoclaw.bot.api.dto.conversation.request.ApprovalDecisionRequest;
import ai.nomoclaw.bot.api.dto.conversation.request.CreateConversationRequest;
import ai.nomoclaw.bot.api.dto.conversation.request.MessageRequest;
import ai.nomoclaw.bot.api.dto.conversation.request.UpdateConversationPinnedRequest;
import ai.nomoclaw.bot.api.dto.conversation.request.UpdateConversationTitleRequest;
import ai.nomoclaw.bot.api.dto.conversation.response.ApprovalDecisionResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessageResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessageRunResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationSummaryResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.CreateConversationResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.MessageResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.UploadFilesResponse;
import ai.nomoclaw.bot.api.mapper.ConversationApiMapper;
import ai.nomoclaw.bot.orchestrator.ApprovalAppService;
import ai.nomoclaw.bot.orchestrator.ConversationAppService;
import ai.nomoclaw.bot.orchestrator.ConversationAttachmentAppService;
import ai.nomoclaw.bot.orchestrator.MessageRunAppService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.util.List;

/**
 * Conversation, message, attachment, and approval endpoints.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class ConversationController {

    private final ConversationAppService conversationAppService;
    private final ConversationAttachmentAppService conversationAttachmentAppService;
    private final MessageRunAppService messageRunAppService;
    private final ApprovalAppService approvalAppService;

    public ConversationController(ConversationAppService conversationAppService,
                                  ConversationAttachmentAppService conversationAttachmentAppService,
                                  MessageRunAppService messageRunAppService,
                                  ApprovalAppService approvalAppService) {
        this.conversationAppService = conversationAppService;
        this.conversationAttachmentAppService = conversationAttachmentAppService;
        this.messageRunAppService = messageRunAppService;
        this.approvalAppService = approvalAppService;
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
        return ConversationApiMapper.toConversationSummaries(conversationAppService.listConversations());
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

    @GetMapping("/conversations/{conversationUid}/messages")
    public List<ConversationMessageResponse> listMessages(@PathVariable String conversationUid) {
        log.info("[AgentAPI] listMessages conversationUid={}", conversationUid);
        return ConversationApiMapper.toConversationMessages(conversationAppService.listMessages(conversationUid));
    }

    @GetMapping("/conversations/{conversationUid}/message-runs")
    public List<ConversationMessageRunResponse> listMessageRuns(@PathVariable String conversationUid) {
        log.info("[AgentAPI] listMessageRuns conversationUid={}", conversationUid);
        return ConversationApiMapper.toMessageRuns(conversationAppService.listMessageRuns(conversationUid));
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
                        .map(ConversationApiMapper::toConversationAttachment)
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

    @PostMapping("/conversations/{conversationUid}/cancel")
    public SimpleResponse cancelMessage(@PathVariable String conversationUid) {
        log.info("[AgentAPI] cancelMessage conversationUid={}", conversationUid);
        messageRunAppService.cancelLatestMessage(conversationUid);
        return new SimpleResponse("canceled");
    }

    @GetMapping(value = "/conversations/{conversationUid}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String conversationUid) {
        log.info("[AgentAPI] subscribeEvents conversationUid={}", conversationUid);
        return messageRunAppService.subscribe(conversationUid);
    }
}
