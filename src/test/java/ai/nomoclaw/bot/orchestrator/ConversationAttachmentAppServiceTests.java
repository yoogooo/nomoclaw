package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.modelconfig.ModelCatalogService;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.modelconfig.ModelMetadata;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.repository.AgentMessageAttachmentRepository;
import ai.nomoclaw.bot.util.LocalizedMessages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConversationAttachmentAppServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectSingleFileLargerThanChatUploadLimit() {
        AgentStore store = mock(AgentStore.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        LocalizedMessages localizedMessages = mock(LocalizedMessages.class);
        doReturn(Optional.of(conversation("conversation-1"))).when(store).findConversation("conversation-1");
        doReturn(modelConfig("aliyun-codingplan", "qwen3.5-plus")).when(modelConfigAppService).getModelConfig();
        doReturn("文件大小超过 2MB。").when(localizedMessages).get("api.error.uploadFileSizeExceeded", "2MB");
        doReturn(new ModelMetadata(
                "aliyun-codingplan",
                "qwen3.5-plus",
                "Qwen3.5 Plus",
                List.of("text"),
                List.of("text"),
                true,
                0,
                0,
                0,
                new ModelConfigDto.UploadPolicy(true, List.of("image", "pdf", "text"), 1, 5, 20L * 1024 * 1024, 100L * 1024 * 1024, true, false),
                true,
                "builtin",
                "high"
        )).when(modelCatalogService).resolve("aliyun-codingplan", "qwen3.5-plus");
        ConversationAttachmentAppService service = new ConversationAttachmentAppService(
                store,
                attachmentRepository,
                modelConfigAppService,
                modelCatalogService,
                localizedMessages,
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(100)
        );

        MockMultipartFile oversizedImage = new MockMultipartFile(
                "files",
                "oversized.png",
                "image/png",
                new byte[3 * 1024 * 1024]
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.uploadFiles("conversation-1", "aliyun-codingplan", "qwen3.5-plus", List.of(oversizedImage)));

        assertTrue(error.getMessage().contains("文件大小超过 2MB"));
    }

    @Test
    void shouldAttachExistingImageFileToMessage() throws Exception {
        AgentStore store = mock(AgentStore.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        LocalizedMessages localizedMessages = mock(LocalizedMessages.class);
        doReturn(Optional.of(conversation("conversation-1"))).when(store).findConversation("conversation-1");
        doReturn(null).when(attachmentRepository).findActiveByMessageUidAndFilePath(any(), any());

        ConversationAttachmentAppService service = new ConversationAttachmentAppService(
                store,
                attachmentRepository,
                modelConfigAppService,
                modelCatalogService,
                localizedMessages,
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(100)
        );

        Path imagePath = Files.writeString(tempDir.resolve("desktop_screenshot_test.png"), "image");
        var contents = service.attachExistingImageFilesToMessage("conversation-1", "message-1", List.of(imagePath.toString()));

        assertEquals(1, contents.size());
        verify(attachmentRepository).save(any());
    }

    @Test
    void shouldSkipPersistWhenExistingImageAttachmentAlreadyBound() throws Exception {
        AgentStore store = mock(AgentStore.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        LocalizedMessages localizedMessages = mock(LocalizedMessages.class);
        doReturn(Optional.of(conversation("conversation-1"))).when(store).findConversation("conversation-1");

        Path imagePath = Files.writeString(tempDir.resolve("desktop_screenshot_existing.png"), "image");
        var existing = new ai.nomoclaw.bot.store.entity.AgentMessageAttachmentEntity();
        existing.setFilePath(imagePath.toString());
        existing.setContentType("image/png");
        existing.setMimeGroup("image");
        existing.setOriginalName("desktop_screenshot_existing.png");
        doReturn(existing).when(attachmentRepository).findActiveByMessageUidAndFilePath("message-1", imagePath.toString());

        ConversationAttachmentAppService service = new ConversationAttachmentAppService(
                store,
                attachmentRepository,
                modelConfigAppService,
                modelCatalogService,
                localizedMessages,
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(100)
        );

        var contents = service.attachExistingImageFilesToMessage("conversation-1", "message-1", List.of(imagePath.toString()));

        assertEquals(1, contents.size());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenExistingImagePathDoesNotExist() {
        AgentStore store = mock(AgentStore.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        LocalizedMessages localizedMessages = mock(LocalizedMessages.class);
        doReturn(Optional.of(conversation("conversation-1"))).when(store).findConversation("conversation-1");

        ConversationAttachmentAppService service = new ConversationAttachmentAppService(
                store,
                attachmentRepository,
                modelConfigAppService,
                modelCatalogService,
                localizedMessages,
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(100)
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.attachExistingImageFilesToMessage(
                        "conversation-1",
                        "message-1",
                        List.of(tempDir.resolve("missing.png").toString()))
        );
        assertTrue(error.getMessage().contains("image file not found"));
    }

    @Test
    void shouldLoadExistingImageContentsWithoutBindingMessageAttachment() throws Exception {
        AgentStore store = mock(AgentStore.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        LocalizedMessages localizedMessages = mock(LocalizedMessages.class);

        ConversationAttachmentAppService service = new ConversationAttachmentAppService(
                store,
                attachmentRepository,
                modelConfigAppService,
                modelCatalogService,
                localizedMessages,
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(100)
        );

        Path imagePath = Files.writeString(tempDir.resolve("desktop_screenshot_ctx_only.png"), "image");
        var contents = service.loadExistingImageFilesAsContents(List.of(imagePath.toString()));

        assertEquals(1, contents.size());
        verify(attachmentRepository, never()).save(any());
    }

    private AgentConversation conversation(String conversationUid) {
        return new AgentConversation(
                conversationUid,
                "group-1",
                "agent-1",
                "",
                "title",
                false,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );
    }

    private ModelConfigDto modelConfig(String providerId, String modelId) {
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
                                List.of("text"),
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
