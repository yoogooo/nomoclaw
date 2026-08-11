package ai.nomoclaw.bot.conversation.support;

import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentMessageAttachmentEntity;
import ai.nomoclaw.bot.store.repository.AgentMessageAttachmentRepository;
import ai.nomoclaw.bot.util.LocalizedMessages;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationAttachmentServiceTests {

    private AgentMessageAttachmentRepository attachmentRepository;
    private ConversationAttachmentService service;

    @BeforeEach
    void setUp() {
        attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        ModelConfigAppService modelConfigAppService = mock(ModelConfigAppService.class);
        ModelConfigDto.Model model = new ModelConfigDto.Model(
                "gemma4:e4b",
                "Gemma 4",
                "TEXT_GENERATION",
                new ModelConfigDto.ModelCapabilities(false, false, false, false, false),
                32768,
                32768,
                4096,
                true,
                "test"
        );
        ModelConfigDto.Provider provider = new ModelConfigDto.Provider(
                "ollama",
                "Ollama",
                "ollama",
                true,
                false,
                false,
                "http://127.0.0.1:11434",
                "",
                true,
                "",
                "",
                "gemma4:e4b",
                List.of(model)
        );
        when(modelConfigAppService.getModelConfig()).thenReturn(new ModelConfigDto(List.of(provider)));

        service = new ConversationAttachmentService(
                mock(AgentStore.class),
                attachmentRepository,
                modelConfigAppService,
                mock(LocalizedMessages.class),
                mock(DocumentParser.class),
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(100),
                10
        );
    }

    @Test
    void shouldMergeTextAttachmentsBeforeUserInput(@TempDir Path tempDir) throws IOException {
        AgentMessageAttachmentEntity first = textAttachment(1L, "first.txt", tempDir, "第一份文件");
        AgentMessageAttachmentEntity second = textAttachment(2L, "second.txt", tempDir, "第二份文件");
        when(attachmentRepository.listByMessageUid("message-1")).thenReturn(List.of(first, second));

        List<Content> contents = service.buildContentsForMessage(
                "用户问题：有多少字符",
                "message-1",
                "ollama",
                "gemma4:e4b"
        );

        assertEquals(1, contents.size());
        TextContent text = assertInstanceOf(TextContent.class, contents.getFirst());
        assertEquals("文件(first.txt):\n第一份文件\n\n文件(second.txt):\n第二份文件\n\n用户问题：有多少字符", text.text());
    }

    @Test
    void shouldCreateOneTextContentForUserInputOnly() {
        when(attachmentRepository.listByMessageUid("message-1")).thenReturn(List.of());

        List<Content> contents = service.buildContentsForMessage(
                "  用户问题  ",
                "message-1",
                "ollama",
                "gemma4:e4b"
        );

        assertEquals(1, contents.size());
        assertEquals("用户问题", assertInstanceOf(TextContent.class, contents.getFirst()).text());
    }

    @Test
    void shouldIgnoreBlankTextAndAvoidEmptyContent(@TempDir Path tempDir) throws IOException {
        AgentMessageAttachmentEntity blank = textAttachment(1L, "blank.txt", tempDir, "   ");
        when(attachmentRepository.listByMessageUid("message-1")).thenReturn(List.of(blank));

        List<Content> contents = service.buildContentsForMessage(
                "  ",
                "message-1",
                "ollama",
                "gemma4:e4b"
        );

        assertEquals(0, contents.size());
    }

    @Test
    void shouldKeepMediaContentSeparateWhileMergingText(@TempDir Path tempDir) throws IOException {
        AgentMessageAttachmentEntity textAttachment = textAttachment(1L, "notes.txt", tempDir, "文件内容");
        AgentMessageAttachmentEntity imageAttachment = imageAttachment(2L, "image.png", tempDir);
        when(attachmentRepository.listByMessageUid("message-1")).thenReturn(List.of(textAttachment, imageAttachment));

        List<Content> contents = service.buildContentsForMessage(
                "用户输入",
                "message-1",
                "ollama",
                "gemma4:e4b"
        );

        assertEquals(2, contents.size());
        assertInstanceOf(TextContent.class, contents.get(0));
        assertInstanceOf(ImageContent.class, contents.get(1));
        assertEquals("文件(notes.txt):\n文件内容\n\n用户输入", ((TextContent) contents.get(0)).text());
    }

    private AgentMessageAttachmentEntity textAttachment(Long id, String name, Path tempDir, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        AgentMessageAttachmentEntity attachment = baseAttachment(id, name, path);
        attachment.setContentType("text/plain");
        attachment.setMimeGroup("text");
        return attachment;
    }

    private AgentMessageAttachmentEntity imageAttachment(Long id, String name, Path tempDir) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, new byte[]{0, 1, 2});
        AgentMessageAttachmentEntity attachment = baseAttachment(id, name, path);
        attachment.setContentType("image/png");
        attachment.setMimeGroup("image");
        return attachment;
    }

    private AgentMessageAttachmentEntity baseAttachment(Long id, String name, Path path) {
        AgentMessageAttachmentEntity attachment = new AgentMessageAttachmentEntity();
        attachment.setId(id);
        attachment.setOriginalName(name);
        attachment.setFilePath(path.toString());
        attachment.setStatus("ACTIVE");
        return attachment;
    }
}
