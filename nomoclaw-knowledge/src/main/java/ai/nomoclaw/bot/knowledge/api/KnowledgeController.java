package ai.nomoclaw.bot.knowledge.api;

import ai.nomoclaw.bot.knowledge.app.KnowledgeService;
import ai.nomoclaw.bot.knowledge.model.KnowledgeModels;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST API for knowledge-base management and bindings.
 */
@RestController
@RequestMapping("/api")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    /**
     * Creates a knowledge base and assigns its immutable Qdrant collection name.
     */
    @PostMapping("/knowledge-bases")
    public KnowledgeModels.Base create(@RequestBody KnowledgeModels.CreateRequest request) {
        return service.create(request);
    }

    /**
     * Lists non-deleting knowledge bases and the current vector-service availability.
     */
    @GetMapping("/knowledge-bases/page")
    public Map<String, Object> list(@RequestParam(required = false) String keyword) {
        List<KnowledgeModels.Base> items = service.list(keyword);
        return Map.of("items", items, "total", items.size(), "vectorAvailable", service.vectorAvailable());
    }

    /**
     * Returns one knowledge base by its business UID.
     */
    @GetMapping("/knowledge-bases/{uid}")
    public KnowledgeModels.Base get(@PathVariable String uid) {
        return service.get(uid);
    }

    /**
     * Updates mutable knowledge-base settings without renaming its vector collection.
     */
    @PatchMapping("/knowledge-bases/{uid}")
    public KnowledgeModels.Base update(@PathVariable String uid, @RequestBody KnowledgeModels.UpdateRequest request) {
        return service.update(uid, request);
    }

    /**
     * Accepts documents and creates asynchronous ingestion jobs for them.
     */
    @PostMapping(value = "/knowledge-bases/{uid}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeModels.UploadResult> upload(@PathVariable String uid, @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.accepted().body(new KnowledgeModels.UploadResult(service.upload(uid, files)));
    }

    /**
     * Lists documents and their latest ingestion-job progress for a knowledge base.
     */
    @GetMapping("/knowledge-bases/{uid}/documents/page")
    public Map<String, Object> documents(@PathVariable String uid) {
        List<KnowledgeModels.Document> items = service.listDocuments(uid);
        return Map.of("items", items, "total", items.size());
    }

    /**
     * Returns one uploaded document and its latest ingestion status.
     */
    @GetMapping("/knowledge-bases/{uid}/documents/{documentUid}")
    public KnowledgeModels.Document document(@PathVariable String uid, @PathVariable String documentUid) {
        return service.getDocument(uid, documentUid);
    }

    /**
     * Resubmits a failed document ingestion job.
     */
    @PostMapping("/knowledge-bases/{uid}/documents/{documentUid}/retry")
    public KnowledgeModels.StatusResponse retry(@PathVariable String uid, @PathVariable String documentUid) {
        service.retry(uid, documentUid);
        return new KnowledgeModels.StatusResponse("accepted");
    }

    /**
     * Downloads the original file after resolving it through the document record.
     */
    @GetMapping("/knowledge-bases/{uid}/documents/{documentUid}/content")
    public ResponseEntity<Resource> content(@PathVariable String uid, @PathVariable String documentUid) {
        Path path = service.documentPath(uid, documentUid);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(path.getFileName().toString()).build().toString()).body(new FileSystemResource(path));
    }

    /**
     * Runs a debug similarity search within one knowledge base.
     */
    @PostMapping("/knowledge-bases/{uid}/search")
    public List<KnowledgeModels.SearchHit> search(@PathVariable String uid, @RequestBody KnowledgeModels.SearchRequest request) {
        return service.search(uid, request.query(), request.topK());
    }

    /**
     * Returns the Agent's enabled default knowledge bases.
     */
    @GetMapping("/agents/{agentUid}/knowledge-bases")
    public List<String> agentBindings(@PathVariable String agentUid) {
        return service.getAgentBinding(agentUid);
    }

    /**
     * Replaces the Agent's default knowledge-base bindings.
     */
    @PutMapping("/agents/{agentUid}/knowledge-bases")
    public KnowledgeModels.StatusResponse setAgentBindings(@PathVariable String agentUid, @RequestBody List<String> uids) {
        service.setAgentBinding(agentUid, uids);
        return new KnowledgeModels.StatusResponse("updated");
    }

    /**
     * Returns effective, included, and excluded knowledge bases for a conversation.
     */
    @GetMapping("/conversations/{conversationUid}/knowledge-bases")
    public KnowledgeModels.Binding conversationBindings(@PathVariable String conversationUid) {
        return service.getConversationBinding(conversationUid);
    }

    /**
     * Replaces conversation-level include and exclude overrides.
     */
    @PutMapping("/conversations/{conversationUid}/knowledge-bases")
    public KnowledgeModels.StatusResponse setConversationBindings(@PathVariable String conversationUid, @RequestBody KnowledgeModels.BindingRequest request) {
        service.setConversationBinding(conversationUid, request);
        return new KnowledgeModels.StatusResponse("updated");
    }
}
