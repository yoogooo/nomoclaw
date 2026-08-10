package ai.nomoclaw.bot.conversation.support;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.conversation.model.ConversationAttachmentDto;
import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;
import ai.nomoclaw.bot.modelconfig.ModelCatalogService;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.modelconfig.ModelTypes;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser;
import ai.nomoclaw.bot.store.entity.AgentMessageAttachmentEntity;
import ai.nomoclaw.bot.store.repository.AgentMessageAttachmentRepository;
import ai.nomoclaw.bot.util.LocalizedMessages;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.data.message.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConversationAttachmentService {

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
    private final LocalizedMessages localizedMessages;
    private final DocumentParser documentParser;
    private final long maxChatUploadFileBytes;
    private final long maxChatUploadRequestBytes;
    private final int maxChatUploadFilesPerMessage;

    public ConversationAttachmentService(AgentStore store,
                                            AgentMessageAttachmentRepository attachmentRepository,
                                            ModelConfigAppService modelConfigAppService,
                                            LocalizedMessages localizedMessages,
                                            DocumentParser documentParser,
                                            @Value("${agent.api.chat-upload.max-file-size:2MB}") DataSize maxChatUploadFileSize,
                                            @Value("${agent.api.chat-upload.max-request-size:100MB}") DataSize maxChatUploadRequestSize,
                                            @Value("${agent.api.chat-upload.max-files-per-message:10}") int maxChatUploadFilesPerMessage) {
        this.store = store;
        this.attachmentRepository = attachmentRepository;
        this.modelConfigAppService = modelConfigAppService;
        this.localizedMessages = localizedMessages;
        this.documentParser = documentParser;
        this.maxChatUploadFileBytes = maxChatUploadFileSize.toBytes();
        this.maxChatUploadRequestBytes = maxChatUploadRequestSize.toBytes();
        this.maxChatUploadFilesPerMessage = Math.max(1, maxChatUploadFilesPerMessage);
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
        ModelConfigDto.Model model = resolveModel(modelProvider, modelName);
        List<PendingAttachment> incoming = normalizedFiles.stream().map(this::toPendingAttachment).toList();
        validateAttachments(model, incoming);

        Path uploadRoot = ensureConversationUploadRoot(conversationUid);
        LocalDateTime now = LocalDateTime.now();
        List<AgentMessageAttachmentEntity> entities = new ArrayList<>();
        for (int i = 0; i < normalizedFiles.size(); i++) {
            MultipartFile file = normalizedFiles.get(i);
            PendingAttachment pending = incoming.get(i);
            String uploadUid = UuidUtil.newUuid();
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
        ModelConfigDto.Model model = resolveModel(modelProvider, modelName);
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
        validateAttachments(model, selected);
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

    public List<Content> buildContentsForMessage(String messageText, String messageUid, String modelProvider, String modelName) {
        List<Content> contents = new ArrayList<>();
        String normalizedText = messageText == null ? "" : messageText.trim();
        if (!normalizedText.isBlank()) {
            contents.add(TextContent.from(normalizedText));
        }
        ModelConfigDto.Model model = resolveModel(modelProvider, modelName);
        for (AgentMessageAttachmentEntity attachment : attachmentRepository.listByMessageUid(messageUid)) {
            contents.add(toContent(attachment, model));
        }
        return contents;
    }

    public List<Content> attachExistingImageFilesToMessage(String conversationUid,
                                                           String messageUid,
                                                           Collection<String> imagePaths) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        if (messageUid == null || messageUid.isBlank()) {
            throw new IllegalArgumentException("messageUid must not be blank");
        }
        List<Path> resolvedPaths = resolveExistingImagePaths(imagePaths);
        if (resolvedPaths.isEmpty()) {
            return List.of();
        }

        List<AgentMessageAttachmentEntity> boundEntities = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Path file : resolvedPaths) {
            String name = file.getFileName() == null ? "" : file.getFileName().toString();
            String contentType = normalizeContentType(detectImageContentType(file));

            AgentMessageAttachmentEntity existing = attachmentRepository.findActiveByMessageUidAndFilePath(messageUid, file.toString());
            if (existing != null) {
                boundEntities.add(existing);
                continue;
            }

            AgentMessageAttachmentEntity entity = new AgentMessageAttachmentEntity();
            entity.setUploadUid(UuidUtil.newUuid());
            entity.setConversationUid(conversationUid);
            entity.setMessageUid(messageUid);
            entity.setOriginalName(name.isBlank() ? "image" : name);
            entity.setContentType(contentType);
            entity.setMimeGroup("image");
            entity.setFilePath(file.toString());
            entity.setFileUrl(buildFileUrl(conversationUid, entity.getUploadUid()));
            try {
                entity.setSizeBytes(Files.size(file));
            } catch (IOException ex) {
                throw new IllegalStateException("failed to read image size: " + file, ex);
            }
            entity.setPreviewable(1);
            entity.setStatus("ACTIVE");
            entity.setCreatedTime(now);
            entity.setUpdatedTime(now);
            attachmentRepository.save(entity);
            boundEntities.add(entity);
        }

        List<Content> contents = new ArrayList<>();
        for (AgentMessageAttachmentEntity attachment : boundEntities) {
            contents.add(toContent(attachment));
        }
        return contents;
    }

    public List<Content> loadExistingImageFilesAsContents(Collection<String> imagePaths) {
        List<Path> resolvedPaths = resolveExistingImagePaths(imagePaths);
        if (resolvedPaths.isEmpty()) {
            return List.of();
        }
        List<Content> contents = new ArrayList<>();
        for (Path file : resolvedPaths) {
            String contentType = normalizeContentType(detectImageContentType(file));
            contents.add(ImageContent.from(file, contentType));
        }
        return contents;
    }

    private List<Path> resolveExistingImagePaths(Collection<String> imagePaths) {
        List<String> normalizedPaths = imagePaths == null ? List.of() : imagePaths.stream()
                .map(path -> path == null ? "" : path.trim())
                .filter(path -> !path.isBlank())
                .map(path -> Path.of(path).toAbsolutePath().normalize().toString())
                .distinct()
                .toList();
        if (normalizedPaths.isEmpty()) {
            return List.of();
        }
        List<Path> resolved = new ArrayList<>();
        for (String normalizedPath : normalizedPaths) {
            Path file = Path.of(normalizedPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("image file not found: " + normalizedPath);
            }
            String name = file.getFileName() == null ? "" : file.getFileName().toString();
            String contentType = normalizeContentType(detectImageContentType(file));
            String mimeGroup = classifyMimeGroup(contentType, name);
            if (!"image".equals(mimeGroup)) {
                throw new IllegalArgumentException("unsupported image file type: " + normalizedPath);
            }
            resolved.add(file);
        }
        return resolved;
    }

    private Content toContent(AgentMessageAttachmentEntity attachment) {
        return toContent(attachment, null);
    }

    private Content toContent(AgentMessageAttachmentEntity attachment, ModelConfigDto.Model model) {
        Path path = Path.of(attachment.getFilePath()).toAbsolutePath().normalize();
        String mimeGroup = normalizeMimeGroup(attachment.getMimeGroup());
        String contentType = normalizeContentType(attachment.getContentType());
        try {
            return switch (mimeGroup) {
                case "image" -> ImageContent.from(path, contentType);
                case "pdf" -> TextContent.from(pdfText(attachment, path));
                case "audio" -> AudioContent.from(path, contentType);
                case "video" -> VideoContent.from(path, contentType);
                case "text" -> TextContent.from("文件(" + attachment.getOriginalName() + "):\n" + Files.readString(path, StandardCharsets.UTF_8));
                default -> throw new IllegalArgumentException("unsupported attachment type: " + attachment.getOriginalName());
            };
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read attachment: " + attachment.getOriginalName(), ex);
        }
    }

    private void validateAttachments(ModelConfigDto.Model model, List<PendingAttachment> incoming) {
        if (!ModelTypes.TEXT_GENERATION.equals(model.modelType())) {
            throw new IllegalArgumentException(localizedMessages.get("api.error.uploadDisabled"));
        }
        if (incoming.size() > maxChatUploadFilesPerMessage) {
            throw new IllegalArgumentException("Too many attachments: maximum " + maxChatUploadFilesPerMessage);
        }
        long totalBytes = incoming.stream().mapToLong(PendingAttachment::sizeBytes).sum();
        if (totalBytes > maxChatUploadRequestBytes) {
            throw new IllegalArgumentException(localizedMessages.get("api.error.uploadTotalSizeExceeded", formatBytes(maxChatUploadRequestBytes)));
        }
        if (maxChatUploadFileBytes > 0 && incoming.stream().anyMatch(item -> item.sizeBytes() > maxChatUploadFileBytes)) {
            throw new IllegalArgumentException(localizedMessages.get("api.error.uploadFileSizeExceeded", formatBytes(maxChatUploadFileBytes)));
        }
        if (incoming.stream().anyMatch(item -> !isSupportedByModel(model.capabilities(), item.mimeGroup()))) {
            throw new IllegalArgumentException(localizedMessages.get("api.error.uploadMimeGroupNotAllowed"));
        }
    }

    private PendingAttachment toPendingAttachment(MultipartFile file) {
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "upload"
                : file.getOriginalFilename().trim();
        String contentType = normalizeContentType(file.getContentType());
        String mimeGroup = classifyMimeGroup(contentType, originalName);
        if ("other".equals(mimeGroup)) {
            throw new IllegalArgumentException(localizedMessages.get("api.error.uploadUnsupportedFileType", originalName));
        }
        return new PendingAttachment(
                originalName,
                contentType,
                mimeGroup,
                Math.max(0L, file.getSize()),
                "image".equals(mimeGroup)
        );
    }

    private ModelConfigDto.Model resolveModel(String modelProvider, String modelName) {
        ModelConfigDto config = modelConfigAppService.getModelConfig();
        for (ModelConfigDto.Provider provider : config.providers()) {
            if (!provider.id().equals(trim(modelProvider))) {
                continue;
            }
            for (ModelConfigDto.Model model : provider.models()) {
                if (model.id().equals(trim(modelName))) {
                    return model;
                }
            }
        }
        throw new IllegalArgumentException("model not configured: " + modelProvider + "/" + modelName);
    }

    private boolean isSupportedByModel(ModelConfigDto.ModelCapabilities capabilities, String mimeGroup) {
        ModelConfigDto.ModelCapabilities normalized = capabilities == null ? ModelConfigDto.ModelCapabilities.none() : capabilities;
        return switch (mimeGroup) {
            case "text", "pdf" -> true;
            case "image" -> normalized.imageRecognition();
            case "audio" -> normalized.audioRecognition();
            case "video" -> normalized.videoRecognition();
            default -> false;
        };
    }

    private String pdfText(AgentMessageAttachmentEntity attachment, Path path) {
        DocumentParser.ParsedDocument parsed = documentParser.parse(path);
        String text = parsed.pages().stream().map(DocumentParser.Page::text).collect(Collectors.joining("\n\n")).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("PDF contains no extractable text: " + attachment.getOriginalName());
        }
        return "文件(" + attachment.getOriginalName() + "):\n" + text;
    }

    private String formatBytes(long bytes) {
        long mb = bytes / (1024L * 1024L);
        if (mb > 0) {
            return mb + "MB";
        }
        return bytes + " bytes";
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
        if (normalizedContentType.startsWith("text/") || TEXT_MIME_TYPES.contains(normalizedContentType) || hasStringExtension(originalName)) {
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

    private boolean hasStringExtension(String originalName) {
        String lower = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".xml");
    }

    private int sanitizeNonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private long sanitizeNonNegative(Long value) {
        return value == null || value < 0 ? 0L : value;
    }

    private long minPositive(long left, long right) {
        if (left <= 0) {
            return right;
        }
        if (right <= 0) {
            return left;
        }
        return Math.min(left, right);
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

    private String detectImageContentType(Path file) {
        try {
            String detected = Files.probeContentType(file);
            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (IOException ignored) {
            // fallback to extension based detection
        }
        String byName = URLConnection.guessContentTypeFromName(file.getFileName() == null ? "" : file.getFileName().toString());
        if (byName != null && !byName.isBlank()) {
            return byName;
        }
        String extension = extensionOf(file.getFileName() == null ? "" : file.getFileName().toString());
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
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
