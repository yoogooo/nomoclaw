package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.repository.AgentMessageAttachmentRepository;
import ai.nomoclaw.bot.util.LocalizedMessages;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ConversationAttachmentAppServiceTests {

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

    private AgentConversation conversation(String conversationUid) {
        return new AgentConversation(
                conversationUid,
                "group-1",
                "agent-1",
                "",
                "title",
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
