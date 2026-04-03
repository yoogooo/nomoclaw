package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ConversationAttachmentDto;
import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentMessageAttachmentEntity;
import ai.nomoclaw.bot.store.repository.AgentMessageAttachmentRepository;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationAttachmentAppService {

    private static final long MAX_REQUEST_SIZE_BYTES = 100L * 1024 * 1024;
    private static final List<String> TEXT_MIME_TYPES = List.of(
            "application/json",
            "application/xml",
            "application/yaml",
            "application/x-yaml",
            "application/csv"
    );

    private final AgentStore store;
    private final AgentMessageAttachmentRepository attachmentRepository;
    private final ModelConfigAppService modelConfigAppService;

    public ConversationAttachmentAppService(AgentStore store,
                                            AgentMessageAttachmentRepository attachmentRepository,
                                            ModelConfigAppService modelConfigAppService) {
        this.store = store;
        this.attachmentRepository = attachmentRepository;
        this.modelConfigAppService = modelConfigAppService;
    }

    public List<ConversationAttachmentDto> uploadFiles(String conversationUid,
                                                       String modelProvider,
                                                       String modelName,
                                                       List<MultipartFile> files) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        List<MultipartFile> normalizedFiles = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (normalizedFiles.isEmpty()) {
            throw new IllegalArgumentException("at least one file is required");
        }
        ModelConfigDto.UploadPolicy policy = resolveUploadPolicy(modelProvider, modelName);
        List<PendingAttachment> incoming = normalizedFiles.stream().map(this::toPendingAttachment).toList();
        validateAttachments(policy, incoming);

        Path uploadRoot = ensureConversationUploadRoot(conversationUid);
        LocalDateTime now = LocalDateTime.now();
        List<AgentMessageAttachmentEntity> entities = new ArrayList<>();
        for (int i = 0; i < normalizedFiles.size(); i++) {
            MultipartFile file = normalizedFiles.get(i);
            PendingAttachment pending = incoming.get(i);
            String uploadUid = UUID.randomUUID().toString();
            String extension = extensionOf(file.getOriginalFilename());
            Path target = uploadRoot.resolve(uploadUid + (extension.isBlank() ? "" : "." + extension));
            try {
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to store upload: " + file.getOriginalFilename(), ex);
            }
            AgentMessageAttachmentEntity entity = new AgentMessageAttachmentEntity();
            entity.setUploadUid(uploadUid);
            entity.setConversationUid(conversationUid);
            entity.setMessageUid("");
            entity.setOriginalName(pending.name());
            entity.setContentType(pending.contentType());
            entity.setMimeGroup(pending.mimeGroup());
            entity.setFilePath(target.toAbsolutePath().normalize().toString());
            entity.setFileUrl(buildFileUrl(conversationUid, uploadUid));
            entity.setSizeBytes(pending.sizeBytes());
            entity.setPreviewable(pending.previewable() ? 1 : 0);
            entity.setStatus("ACTIVE");
            entity.setCreatedTime(now);
            entity.setUpdatedTime(now);
            entities.add(entity);
        }
        attachmentRepository.saveBatch(entities, 100);
        return entities.stream().map(this::toDto).toList();
    }

    public void attachUploadsToMessage(String conversationUid,
                                       String messageUid,
                                       Collection<String> fileUrls,
                                       String modelProvider,
                                       String modelName) {
        List<String> urls = fileUrls == null ? List.of() : fileUrls.stream().filter(url -> url != null && !url.isBlank()).toList();
        if (urls.isEmpty()) {
            return;
        }
        ModelConfigDto.UploadPolicy policy = resolveUploadPolicy(modelProvider, modelName);
        List<PendingAttachment> selected = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String url : urls) {
            String uploadUid = extractUploadUid(conversationUid, url);
            AgentMessageAttachmentEntity entity = attachmentRepository.findByUploadUid(uploadUid);
            if (entity == null || !conversationUid.equals(entity.getConversationUid())) {
                throw new IllegalArgumentException("fileUrl does not belong to conversation: " + url);
            }
            if (entity.getMessageUid() != null && !entity.getMessageUid().isBlank() && !messageUid.equals(entity.getMessageUid())) {
                throw new IllegalArgumentException("fileUrl already attached: " + url);
            }
            selected.add(PendingAttachment.fromEntity(entity));
        }
        validateAttachments(policy, selected);
        for (String url : urls) {
            String uploadUid = extractUploadUid(conversationUid, url);
            attachmentRepository.update(new LambdaUpdateWrapper<AgentMessageAttachmentEntity>()
                    .eq(AgentMessageAttachmentEntity::getUploadUid, uploadUid)
                    .set(AgentMessageAttachmentEntity::getMessageUid, messageUid)
                    .set(AgentMessageAttachmentEntity::getUpdatedTime, now));
        }
    }

    public List<ConversationAttachmentDto> listByMessageUid(String messageUid) {
        return attachmentRepository.listByMessageUid(messageUid).stream().map(this::toDto).toList();
    }

    public Map<String, List<ConversationAttachmentDto>> listByMessageUids(Collection<String> messageUids) {
        if (messageUids == null || messageUids.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.listByMessageUids(messageUids).stream()
                .collect(Collectors.groupingBy(
                        AgentMessageAttachmentEntity::getMessageUid,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toDto, Collectors.toList())
                ));
    }

    public AgentMessageAttachmentEntity requireAttachment(String conversationUid, String uploadUid) {
        AgentMessageAttachmentEntity entity = attachmentRepository.findByUploadUid(uploadUid);
        if (entity == null || !conversationUid.equals(entity.getConversationUid()) || !"ACTIVE".equalsIgnoreCase(entity.getStatus())) {
            throw new IllegalArgumentException("attachment not found: " + uploadUid);
        }
        return entity;
    }

    public void purgeConversationAttachments(String conversationUid) {
        String normalizedConversationUid = trim(conversationUid);
        if (normalizedConversationUid.isBlank()) {
            return;
        }
        attachmentRepository.deleteByConversationUid(normalizedConversationUid);
        Path uploadDir = NomoClawPaths.root().resolve("uploads").resolve(normalizedConversationUid).toAbsolutePath().normalize();
        if (!Files.exists(uploadDir)) {
            return;
        }
        try {
            Files.walkFileTree(uploadDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("failed to cleanup upload dir: " + uploadDir, ex);
        }
    }

    public List<Content> buildContentsForMessage(String messageText, String messageUid) {
        List<Content> contents = new ArrayList<>();
        String normalizedText = messageText == null ? "" : messageText.trim();
        if (!normalizedText.isBlank()) {
            contents.add(TextContent.from(normalizedText));
        }
        for (AgentMessageAttachmentEntity attachment : attachmentRepository.listByMessageUid(messageUid)) {
            contents.add(toContent(attachment));
        }
        return contents;
    }

    private Content toContent(AgentMessageAttachmentEntity attachment) {
        Path path = Path.of(attachment.getFilePath()).toAbsolutePath().normalize();
        String mimeGroup = normalizeMimeGroup(attachment.getMimeGroup());
        String contentType = normalizeContentType(attachment.getContentType());
        try {
            return switch (mimeGroup) {
                case "image" -> ImageContent.from(path, contentType);
                case "pdf" -> PdfFileContent.from(path);
                case "audio" -> AudioContent.from(path, contentType);
                case "video" -> VideoContent.from(path, contentType);
                case "text" -> TextContent.from("文件(" + attachment.getOriginalName() + "):\n" + Files.readString(path, StandardCharsets.UTF_8));
                default -> throw new IllegalArgumentException("unsupported attachment type: " + attachment.getOriginalName());
            };
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read attachment: " + attachment.getOriginalName(), ex);
        }
    }

    private void validateAttachments(ModelConfigDto.UploadPolicy policy,
                                     List<PendingAttachment> incoming) {
        if (!policy.enabled()) {
            throw new IllegalArgumentException("current model does not allow file upload");
        }
        long totalBytes = incoming.stream().mapToLong(PendingAttachment::sizeBytes).sum();
        if (totalBytes > MAX_REQUEST_SIZE_BYTES) {
            throw new IllegalArgumentException("upload size exceeds 100MB");
        }
        if (incoming.isEmpty()) {
            return;
        }
        LinkedHashSet<String> groups = incoming.stream().map(PendingAttachment::mimeGroup).collect(Collectors.toCollection(LinkedHashSet::new));
        if (policy.singleMimeGroupOnly() && groups.size() > 1) {
            throw new IllegalArgumentException("all files in one message must share the same type");
        }
        String firstGroup = incoming.get(0).mimeGroup();
        if (!policy.allowedMimeGroups().isEmpty() && !policy.allowedMimeGroups().contains(firstGroup)) {
            throw new IllegalArgumentException("current model does not allow this file type");
        }
        if (policy.singleMimeGroupOnly() && !policy.allowedMimeGroups().isEmpty() && groups.stream().anyMatch(group -> !policy.allowedMimeGroups().contains(group))) {
            throw new IllegalArgumentException("current model does not allow this file type");
        }
        long imageCount = incoming.stream().filter(item -> "image".equals(item.mimeGroup())).count();
        long nonImageCount = incoming.size() - imageCount;
        if (imageCount > 0 && nonImageCount > 0 && !policy.allowMixedImageAndFile()) {
            throw new IllegalArgumentException("image and non-image files cannot be mixed");
        }
        if (imageCount > sanitizeNonNegative(policy.maxImagesPerMessage())) {
            throw new IllegalArgumentException("too many images for current model");
        }
        if (nonImageCount > sanitizeNonNegative(policy.maxFilesPerMessage())) {
            throw new IllegalArgumentException("too many files for current model");
        }
    }

    private PendingAttachment toPendingAttachment(MultipartFile file) {
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "upload"
                : file.getOriginalFilename().trim();
        String contentType = normalizeContentType(file.getContentType());
        String mimeGroup = classifyMimeGroup(contentType, originalName);
        if ("other".equals(mimeGroup)) {
            throw new IllegalArgumentException("unsupported file type: " + originalName);
        }
        return new PendingAttachment(
                originalName,
                contentType,
                mimeGroup,
                Math.max(0L, file.getSize()),
                "image".equals(mimeGroup)
        );
    }

    private ModelConfigDto.UploadPolicy resolveUploadPolicy(String modelProvider, String modelName) {
        ModelConfigDto config = modelConfigAppService.getModelConfig();
        for (ModelConfigDto.Provider provider : config.providers()) {
            if (!provider.id().equals(trim(modelProvider))) {
                continue;
            }
            for (ModelConfigDto.Model model : provider.models()) {
                if (model.id().equals(trim(modelName))) {
                    return model.uploadPolicy() == null
                            ? new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, false, false)
                            : model.uploadPolicy();
                }
            }
        }
        throw new IllegalArgumentException("model not configured: " + modelProvider + "/" + modelName);
    }

    private Path ensureConversationUploadRoot(String conversationUid) {
        try {
            Path root = NomoClawPaths.root().resolve("uploads").resolve(conversationUid).toAbsolutePath().normalize();
            Files.createDirectories(root);
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to initialize upload dir", ex);
        }
    }

    private String buildFileUrl(String conversationUid, String uploadUid) {
        return "/api/conversations/" + conversationUid + "/uploads/" + uploadUid + "/content";
    }

    private String extractUploadUid(String conversationUid, String fileUrl) {
        String prefix = "/api/conversations/" + conversationUid + "/uploads/";
        if (!fileUrl.startsWith(prefix) || !fileUrl.endsWith("/content")) {
            throw new IllegalArgumentException("invalid fileUrl: " + fileUrl);
        }
        String uploadUid = fileUrl.substring(prefix.length(), fileUrl.length() - "/content".length());
        if (uploadUid.isBlank()) {
            throw new IllegalArgumentException("invalid fileUrl: " + fileUrl);
        }
        return uploadUid;
    }

    private ConversationAttachmentDto toDto(AgentMessageAttachmentEntity entity) {
        return new ConversationAttachmentDto(
                entity.getUploadUid(),
                entity.getOriginalName(),
                normalizeContentType(entity.getContentType()),
                normalizeMimeGroup(entity.getMimeGroup()),
                entity.getSizeBytes() == null ? 0L : entity.getSizeBytes(),
                entity.getFileUrl(),
                entity.getPreviewable() != null && entity.getPreviewable() == 1
        );
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "application/octet-stream" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMimeGroup(String mimeGroup) {
        return mimeGroup == null ? "other" : mimeGroup.trim().toLowerCase(Locale.ROOT);
    }

    private String classifyMimeGroup(String contentType, String originalName) {
        String normalizedContentType = normalizeContentType(contentType);
        if (normalizedContentType.startsWith("image/")) {
            return "image";
        }
        if (normalizedContentType.equals("application/pdf") || originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return "pdf";
        }
        if (normalizedContentType.startsWith("text/") || TEXT_MIME_TYPES.contains(normalizedContentType) || hasTextExtension(originalName)) {
            return "text";
        }
        if (normalizedContentType.startsWith("audio/")) {
            return "audio";
        }
        if (normalizedContentType.startsWith("video/")) {
            return "video";
        }
        return "other";
    }

    private boolean hasTextExtension(String originalName) {
        String lower = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".xml");
    }

    private int sanitizeNonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private record PendingAttachment(String name, String contentType, String mimeGroup, long sizeBytes, boolean previewable) {
        private static PendingAttachment fromEntity(AgentMessageAttachmentEntity entity) {
            return new PendingAttachment(
                    entity.getOriginalName(),
                    entity.getContentType(),
                    entity.getMimeGroup(),
                    entity.getSizeBytes() == null ? 0L : entity.getSizeBytes(),
                    entity.getPreviewable() != null && entity.getPreviewable() == 1
            );
        }
    }
}
