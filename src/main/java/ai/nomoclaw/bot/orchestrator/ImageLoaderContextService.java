package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.ToolResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class ImageLoaderContextService {

    private static final String MODEL_NOT_MULTIMODAL_CODE = "MODEL_NOT_MULTIMODAL";

    private final ConversationAttachmentAppService conversationAttachmentAppService;
    private final ModelConfigAppService modelConfigAppService;

    public ImageLoaderContextService(ConversationAttachmentAppService conversationAttachmentAppService,
                                     ModelConfigAppService modelConfigAppService) {
        this.conversationAttachmentAppService = conversationAttachmentAppService;
        this.modelConfigAppService = modelConfigAppService;
    }

    public IntegrationResult integrate(AgentMessage message, PlanStep step, ToolResult result) {
        if (message == null || step == null || result == null) {
            return IntegrationResult.noop();
        }
        if (!"ImageLoaderTool".equals(step.toolName()) || !result.success()) {
            return IntegrationResult.noop();
        }
        ResolvedImages resolvedImages = resolveImages(result.artifacts());
        if (resolvedImages.totalCount() == 0) {
            return IntegrationResult.noop();
        }
        if (!supportsImageInput(message.provider(), message.modelName())) {
            return IntegrationResult.withMemoryLine(
                    "warningCode=" + MODEL_NOT_MULTIMODAL_CODE + "\n"
                            + "warningMessage=当前模型不支持图像输入，已跳过视觉上下文注入。");
        }

        try {
            List<Content> imageContents = new ArrayList<>();
            if (!resolvedImages.localPaths().isEmpty()) {
                imageContents.addAll(conversationAttachmentAppService.loadExistingImageFilesAsContents(resolvedImages.localPaths()));
            }
            imageContents.addAll(toExternalImageContents(resolvedImages.externalUrls()));
            if (imageContents.isEmpty()) {
                return IntegrationResult.noop();
            }
            List<Content> contents = new ArrayList<>();
            contents.add(TextContent.from("以下图片已加载到当前上下文，请结合图片继续分析并纠正问题。"));
            contents.addAll(imageContents);
            ChatMessage injectedMessage = UserMessage.from(contents);
            return new IntegrationResult(
                    "visualContextInjected=true\nvisualImageCount=" + imageContents.size(),
                    injectedMessage
            );
        } catch (Exception ex) {
            log.warn("[ImageLoaderContext] inject failed conversationUid={} messageUid={} err={}",
                    message.conversationUid(), message.messageUid(), ex.getMessage());
            return IntegrationResult.withMemoryLine(
                    "warningCode=IMAGE_CONTEXT_INJECTION_FAILED\nwarningMessage=" + safe(ex.getMessage()));
        }
    }

    private boolean supportsImageInput(String providerId, String modelId) {
        String normalizedProvider = safe(providerId).trim();
        String normalizedModel = safe(modelId).trim();
        if (normalizedProvider.isBlank() || normalizedModel.isBlank()) {
            return false;
        }
        ModelConfigDto config = modelConfigAppService.getModelConfig();
        for (ModelConfigDto.Provider provider : config.providers()) {
            if (!normalizedProvider.equals(provider.id())) {
                continue;
            }
            for (ModelConfigDto.Model model : provider.models()) {
                if (!normalizedModel.equals(model.id())) {
                    continue;
                }
                return model.capabilities() != null
                        && model.capabilities().stream()
                        .map(capability -> capability == null ? "" : capability.trim().toLowerCase(Locale.ROOT))
                        .anyMatch("image"::equals);
            }
        }
        return false;
    }

    private ResolvedImages resolveImages(JsonNode artifacts) {
        if (artifacts == null || artifacts.isNull()) {
            return ResolvedImages.empty();
        }
        JsonNode images = artifacts.path("images");
        if (!images.isArray()) {
            return ResolvedImages.empty();
        }
        Set<String> localPaths = new LinkedHashSet<>();
        Set<String> externalUrls = new LinkedHashSet<>();
        for (JsonNode image : images) {
            String path = image.path("path").asText("");
            String url = image.path("url").asText("");
            boolean external = image.path("isExternal").asBoolean(false);
            String normalizedPath = path == null ? "" : path.trim();
            String normalizedUrl = url == null ? "" : url.trim();
            if (external) {
                String candidate = normalizedUrl.isBlank() ? normalizedPath : normalizedUrl;
                if (isHttpUrl(candidate)) {
                    externalUrls.add(candidate);
                }
                continue;
            }
            if (isHttpUrl(normalizedPath)) {
                externalUrls.add(normalizedPath);
                continue;
            }
            if (!normalizedPath.isBlank()) {
                localPaths.add(normalizedPath);
            }
        }
        return new ResolvedImages(new ArrayList<>(localPaths), new ArrayList<>(externalUrls));
    }

    private List<Content> toExternalImageContents(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<Content> contents = new ArrayList<>();
        for (String url : urls) {
            if (!isHttpUrl(url)) {
                continue;
            }
            try {
                contents.add(ImageContent.from(url));
            } catch (Exception ex) {
                log.warn("[ImageLoaderContext] ignore invalid external image url={} err={}", url, ex.getMessage());
            }
        }
        return contents;
    }

    private boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ResolvedImages(List<String> localPaths, List<String> externalUrls) {
        static ResolvedImages empty() {
            return new ResolvedImages(List.of(), List.of());
        }

        int totalCount() {
            return localPaths.size() + externalUrls.size();
        }
    }

    public record IntegrationResult(String memoryLine, ChatMessage injectedMessage) {
        static IntegrationResult noop() {
            return new IntegrationResult("", null);
        }

        static IntegrationResult withMemoryLine(String value) {
            return new IntegrationResult(value == null ? "" : value.trim(), null);
        }
    }
}
