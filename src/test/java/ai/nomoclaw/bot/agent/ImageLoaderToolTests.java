package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.store.entity.AgentEventEntity;
import ai.nomoclaw.bot.store.entity.AgentMessageAttachmentEntity;
import ai.nomoclaw.bot.store.repository.AgentEventRepository;
import ai.nomoclaw.bot.store.repository.AgentMessageAttachmentRepository;
import ai.nomoclaw.bot.tool.ImageLoaderTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageLoaderToolTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldResolveLatestScreenshotFromStepEvents() throws Exception {
        Path first = Files.writeString(tempDir.resolve("desktop_screenshot_old.png"), "old");
        Path latest = Files.writeString(tempDir.resolve("desktop_screenshot_latest.png"), "latest");

        AgentEventRepository eventRepository = mock(AgentEventRepository.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        when(eventRepository.listByConversationUid("c1")).thenReturn(List.of(
                screenshotEvent("c1", "m1", "s1", 1, first, LocalDateTime.now().minusMinutes(2)),
                screenshotEvent("c1", "m1", "s2", 1, latest, LocalDateTime.now().minusMinutes(1))
        ));
        when(attachmentRepository.listActiveByConversationAndMimeGroup("c1", "image")).thenReturn(List.of());

        ImageLoaderTool tool = new ImageLoaderTool(eventRepository, attachmentRepository);
        ToolResult result = tool.execute(request("c1", "m-current", "刚才截图", null));

        assertTrue(result.success());
        assertTrue(result.artifacts().path("matched").asBoolean(false));
        assertEquals(1, result.artifacts().path("resolvedCount").asInt());
        assertEquals(latest.toString(), result.artifacts().path("images").path(0).path("path").asString(""));
    }

    @Test
    void shouldResolveConversationLevelRoundScreenshot() throws Exception {
        Path round1 = Files.writeString(tempDir.resolve("desktop_screenshot_r1.png"), "r1");
        Path round3 = Files.writeString(tempDir.resolve("desktop_screenshot_r3.png"), "r3");

        AgentEventRepository eventRepository = mock(AgentEventRepository.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        when(eventRepository.listByConversationUid("c2")).thenReturn(List.of(
                screenshotEvent("c2", "m1", "s1", 1, round1, LocalDateTime.now().minusMinutes(3)),
                nonScreenshotStepFinishedEvent("c2", "m1", "s2", 2, LocalDateTime.now().minusMinutes(2)),
                screenshotEvent("c2", "m2", "s3", 1, round3, LocalDateTime.now().minusMinutes(1))
        ));
        when(attachmentRepository.listActiveByConversationAndMimeGroup("c2", "image")).thenReturn(List.of());

        ImageLoaderTool tool = new ImageLoaderTool(eventRepository, attachmentRepository);
        ToolResult result = tool.execute(request("c2", "m-current", "第 3 轮截图", null));

        assertTrue(result.success());
        assertTrue(result.artifacts().path("matched").asBoolean(false));
        assertEquals(1, result.artifacts().path("resolvedCount").asInt());
        assertEquals(round3.toString(), result.artifacts().path("images").path(0).path("path").asString(""));
        assertEquals(3, result.artifacts().path("images").path(0).path("conversationRoundIndex").asInt());
    }

    @Test
    void shouldResolveByFileNameFromAttachments() throws Exception {
        Path attachmentPath = Files.writeString(tempDir.resolve("desktop_screenshot_custom.png"), "custom");
        AgentMessageAttachmentEntity attachment = new AgentMessageAttachmentEntity();
        attachment.setFilePath(attachmentPath.toString());
        attachment.setMessageUid("m-attach");
        attachment.setCreatedTime(LocalDateTime.now().minusMinutes(1));

        AgentEventRepository eventRepository = mock(AgentEventRepository.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        when(eventRepository.listByConversationUid("c3")).thenReturn(List.of());
        when(attachmentRepository.listActiveByConversationAndMimeGroup("c3", "image")).thenReturn(List.of(attachment));

        ImageLoaderTool tool = new ImageLoaderTool(eventRepository, attachmentRepository);
        ToolResult result = tool.execute(request("c3", "m-current", "分析 desktop_screenshot_custom.png", 2));

        assertTrue(result.success());
        assertTrue(result.artifacts().path("matched").asBoolean(false));
        assertEquals(1, result.artifacts().path("resolvedCount").asInt());
        assertEquals(attachmentPath.toString(), result.artifacts().path("images").path(0).path("path").asString(""));
        assertEquals("m-attach", result.artifacts().path("images").path(0).path("sourceMessageUid").asString(""));
    }

    @Test
    void shouldResolveImageFromNonScreenshotStepWhenPathIsImage() throws Exception {
        Path browserImage = Files.writeString(tempDir.resolve("browser_capture.png"), "capture");
        AgentEventRepository eventRepository = mock(AgentEventRepository.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        when(eventRepository.listByConversationUid("c5")).thenReturn(List.of(
                stepFinishedEvent("c5", "m1", "s1", 1, "BrowserTool", "extract_text", browserImage, LocalDateTime.now().minusMinutes(1))
        ));
        when(attachmentRepository.listActiveByConversationAndMimeGroup("c5", "image")).thenReturn(List.of());

        ImageLoaderTool tool = new ImageLoaderTool(eventRepository, attachmentRepository);
        ToolResult result = tool.execute(request("c5", "m-current", "分析 browser_capture.png", 1));

        assertTrue(result.success());
        assertTrue(result.artifacts().path("matched").asBoolean(false));
        assertEquals(1, result.artifacts().path("resolvedCount").asInt());
        assertEquals(browserImage.toString(), result.artifacts().path("images").path(0).path("path").asString(""));
        assertFalse(result.artifacts().path("images").path(0).path("isExternal").asBoolean(true));
    }

    @Test
    void shouldResolveHttpImageUrlByPassThrough() {
        AgentEventRepository eventRepository = mock(AgentEventRepository.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        when(eventRepository.listByConversationUid("c6")).thenReturn(List.of());
        when(attachmentRepository.listActiveByConversationAndMimeGroup("c6", "image")).thenReturn(List.of());

        String url = "https://example.com/path/desktop_screenshot_001.png?token=abc";
        ImageLoaderTool tool = new ImageLoaderTool(eventRepository, attachmentRepository);
        ToolResult result = tool.execute(request("c6", "m-current", "分析 " + url, null));

        assertTrue(result.success());
        assertTrue(result.artifacts().path("matched").asBoolean(false));
        assertEquals("url", result.artifacts().path("referenceType").asString(""));
        assertEquals(1, result.artifacts().path("resolvedCount").asInt());
        assertEquals(url, result.artifacts().path("images").path(0).path("path").asString(""));
        assertEquals(url, result.artifacts().path("images").path(0).path("url").asString(""));
        assertTrue(result.artifacts().path("images").path(0).path("isExternal").asBoolean(false));
    }

    @Test
    void shouldReturnUnmatchedWhenNothingFound() {
        AgentEventRepository eventRepository = mock(AgentEventRepository.class);
        AgentMessageAttachmentRepository attachmentRepository = mock(AgentMessageAttachmentRepository.class);
        when(eventRepository.listByConversationUid("c4")).thenReturn(List.of());
        when(attachmentRepository.listActiveByConversationAndMimeGroup("c4", "image")).thenReturn(List.of());

        ImageLoaderTool tool = new ImageLoaderTool(eventRepository, attachmentRepository);
        ToolResult result = tool.execute(request("c4", "m-current", "第 1 轮截图", null));

        assertTrue(result.success());
        assertFalse(result.artifacts().path("matched").asBoolean(true));
        assertEquals(0, result.artifacts().path("resolvedCount").asInt());
    }

    private ToolRequest request(String conversationUid, String messageUid, String reference, Integer maxImages) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("reference", reference);
        if (maxImages != null) {
            args.put("maxImages", maxImages);
        }
        Consumer<ai.nomoclaw.bot.model.ToolProgress> noop = ignored -> {
        };
        return new ToolRequest(
                conversationUid,
                messageUid,
                "step-1",
                "agent-1",
                "general_assistant",
                tempDir,
                tempDir,
                tempDir,
                args,
                10_000L,
                noop
        );
    }

    private AgentEventEntity screenshotEvent(String conversationUid,
                                             String messageUid,
                                             String stepUid,
                                             int roundIndex,
                                             Path outputPath,
                                             LocalDateTime createdTime) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("success", true);
        payload.put("toolName", "DesktopScreenshotTool");
        payload.put("roundIndex", roundIndex);
        payload.put("round", roundIndex);
        payload.putObject("toolArgs");
        payload.putObject("artifacts").put("path", outputPath.toString());

        AgentEventEntity event = new AgentEventEntity();
        event.setConversationUid(conversationUid);
        event.setMessageUid(messageUid);
        event.setStepUid(stepUid);
        event.setEventType("STEP_FINISHED");
        event.setPayload(payload.toString());
        event.setCreatedTime(createdTime);
        return event;
    }

    private AgentEventEntity stepFinishedEvent(String conversationUid,
                                               String messageUid,
                                               String stepUid,
                                               int roundIndex,
                                               String toolName,
                                               String action,
                                               Path outputPath,
                                               LocalDateTime createdTime) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("success", true);
        payload.put("toolName", toolName);
        payload.put("roundIndex", roundIndex);
        payload.put("round", roundIndex);
        payload.putObject("toolArgs").put("action", action);
        payload.putObject("artifacts").put("path", outputPath.toString());

        AgentEventEntity event = new AgentEventEntity();
        event.setConversationUid(conversationUid);
        event.setMessageUid(messageUid);
        event.setStepUid(stepUid);
        event.setEventType("STEP_FINISHED");
        event.setPayload(payload.toString());
        event.setCreatedTime(createdTime);
        return event;
    }

    private AgentEventEntity nonScreenshotStepFinishedEvent(String conversationUid,
                                                            String messageUid,
                                                            String stepUid,
                                                            int roundIndex,
                                                            LocalDateTime createdTime) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("success", true);
        payload.put("toolName", "CommandTool");
        payload.put("roundIndex", roundIndex);
        payload.put("round", roundIndex);
        payload.putObject("toolArgs");
        payload.putObject("artifacts");

        AgentEventEntity event = new AgentEventEntity();
        event.setConversationUid(conversationUid);
        event.setMessageUid(messageUid);
        event.setStepUid(stepUid);
        event.setEventType("STEP_FINISHED");
        event.setPayload(payload.toString());
        event.setCreatedTime(createdTime);
        return event;
    }
}
