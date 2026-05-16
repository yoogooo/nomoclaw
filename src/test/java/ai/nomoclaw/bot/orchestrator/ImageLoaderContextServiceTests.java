package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.RiskLevel;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageLoaderContextServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldInjectUserMessageWhenModelSupportsImage() throws Exception {
        Path image = Files.writeString(tempDir.resolve("desktop_screenshot_ctx.png"), "ctx");
        ConversationAttachmentAppService attachmentAppService = mock(ConversationAttachmentAppService.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        when(modelConfigAppService.getModelConfig()).thenReturn(modelConfig("openai", "gpt-5.4", List.of("text", "image")));
        when(attachmentAppService.loadExistingImageFilesAsContents(any()))
                .thenReturn(List.of(ImageContent.from(image, "image/png")));

        ImageLoaderContextService service = new ImageLoaderContextService(attachmentAppService, modelConfigAppService);
        ImageLoaderContextService.IntegrationResult result = service.integrate(message("openai", "gpt-5.4"), imageLoaderStep(), imageLoaderResult(image));

        assertTrue(result.memoryLine().contains("visualContextInjected=true"));
        assertNotNull(result.injectedMessage());
        assertTrue(result.injectedMessage() instanceof UserMessage);
        verify(attachmentAppService).loadExistingImageFilesAsContents(any());
        verify(attachmentAppService, never()).attachExistingImageFilesToMessage(any(), any(), any());
    }

    @Test
    void shouldSkipInjectionForNonMultimodalModel() throws Exception {
        Path image = Files.writeString(tempDir.resolve("desktop_screenshot_ctx_2.png"), "ctx2");
        ConversationAttachmentAppService attachmentAppService = mock(ConversationAttachmentAppService.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        when(modelConfigAppService.getModelConfig()).thenReturn(modelConfig("openai", "gpt-5.4", List.of("text")));

        ImageLoaderContextService service = new ImageLoaderContextService(attachmentAppService, modelConfigAppService);
        ImageLoaderContextService.IntegrationResult result = service.integrate(message("openai", "gpt-5.4"), imageLoaderStep(), imageLoaderResult(image));

        assertTrue(result.memoryLine().contains("MODEL_NOT_MULTIMODAL"));
        assertNull(result.injectedMessage());
        verify(attachmentAppService, never()).loadExistingImageFilesAsContents(any());
        verify(attachmentAppService, never()).attachExistingImageFilesToMessage(any(), any(), any());
    }

    @Test
    void shouldInjectExternalImageUrlWithoutAttachmentBinding() {
        ConversationAttachmentAppService attachmentAppService = mock(ConversationAttachmentAppService.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        when(modelConfigAppService.getModelConfig()).thenReturn(modelConfig("openai", "gpt-5.4", List.of("text", "image")));

        ImageLoaderContextService service = new ImageLoaderContextService(attachmentAppService, modelConfigAppService);
        ImageLoaderContextService.IntegrationResult result = service.integrate(
                message("openai", "gpt-5.4"),
                imageLoaderStep(),
                imageLoaderUrlResult("https://example.com/vision/sample.png")
        );

        assertTrue(result.memoryLine().contains("visualContextInjected=true"));
        assertNotNull(result.injectedMessage());
        UserMessage injected = (UserMessage) result.injectedMessage();
        long imageCount = injected.contents().stream()
                .filter(ImageContent.class::isInstance)
                .count();
        assertTrue(imageCount >= 1);
        verify(attachmentAppService, never()).loadExistingImageFilesAsContents(any());
        verify(attachmentAppService, never()).attachExistingImageFilesToMessage(any(), any(), any());
    }

    private AgentMessage message(String provider, String modelName) {
        return new AgentMessage(
                "message-1",
                "conversation-1",
                "",
                "user",
                "分析截图",
                MessageStatus.RUNNING,
                provider,
                modelName,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );
    }

    private PlanStep imageLoaderStep() {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("reference", "刚才截图");
        return new PlanStep(
                "step-1",
                1,
                1,
                "加载图片",
                "ImageLoaderTool",
                args,
                RiskLevel.LOW,
                "",
                StepStatus.COMPLETED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
    }

    private ToolResult imageLoaderResult(Path imagePath) {
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("matched", true);
        artifacts.put("resolvedCount", 1);
        ObjectNode image = artifacts.putArray("images").addObject();
        image.put("path", imagePath.toString());
        image.put("name", imagePath.getFileName().toString());
        image.put("sourceMessageUid", "m1");
        image.put("sourceStepUid", "s1");
        image.put("roundIndex", 1);
        image.put("conversationRoundIndex", 1);
        return ToolResult.success("resolved 1 image", artifacts, JsonNodeFactory.instance.objectNode());
    }

    private ToolResult imageLoaderUrlResult(String url) {
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("matched", true);
        artifacts.put("resolvedCount", 1);
        ObjectNode image = artifacts.putArray("images").addObject();
        image.put("path", url);
        image.put("url", url);
        image.put("name", "sample.png");
        image.put("isExternal", true);
        return ToolResult.success("resolved 1 image", artifacts, JsonNodeFactory.instance.objectNode());
    }

    private ModelConfigDto modelConfig(String providerId, String modelId, List<String> capabilities) {
        return new ModelConfigDto(List.of(
                new ModelConfigDto.Provider(
                        providerId,
                        "provider",
                        "OpenAI Compatible",
                        false,
                        true,
                        true,
                        "https://example.com",
                        "",
                        true,
                        "configured",
                        "Provider 已配置",
                        modelId,
                        List.of(new ModelConfigDto.Model(
                                modelId,
                                "model",
                                capabilities,
                                true,
                                0,
                                0,
                                0,
                                null,
                                true,
                                "builtin"
                        ))
                )
        ));
    }
}
