package ai.nomoclaw.bot.llm.debug;

import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.util.JsonUtil;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmDebugJsonlLoggerTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldSkipWriteWhenDisabled() throws Exception {
        LlmDebugProperties properties = new LlmDebugProperties();
        properties.setEnabled(false);
        properties.setFilePath(tempDir.resolve("logs/llm-debug.jsonl").toString());
        LlmDebugJsonlLogger logger = new LlmDebugJsonlLogger(properties);

        logger.logRequest("r1", "task_reason", "openai", "gpt-test",
                PromptLoader.PromptContext.defaultFor("s1", "web"),
                "system",
                List.of(UserMessage.from("hello")),
                ToolChoice.AUTO,
                List.of("toolA"));

        assertFalse(Files.exists(tempDir.resolve("logs/llm-debug.jsonl")));
    }

    @Test
    void shouldCreateDirectoryAndAppendLines() throws Exception {
        Path filePath = tempDir.resolve("logs/llm-debug.jsonl");
        LlmDebugProperties properties = new LlmDebugProperties();
        properties.setEnabled(true);
        properties.setFilePath(filePath.toString());
        LlmDebugJsonlLogger logger = new LlmDebugJsonlLogger(properties);

        logger.logRequest("r1", "task_reason", "openai", "gpt-test",
                PromptLoader.PromptContext.defaultFor("s1", "web"),
                "system1",
                List.of(UserMessage.from("u1")),
                ToolChoice.AUTO,
                List.of("toolA"));
        logger.logRequest("r2", "task_reason", "openai", "gpt-test",
                PromptLoader.PromptContext.defaultFor("s2", "web"),
                "system2",
                List.of(UserMessage.from("u2")),
                ToolChoice.AUTO,
                List.of("toolB"));

        List<String> lines = Files.readAllLines(filePath);
        assertEquals(2, lines.size());
    }

    @Test
    void shouldRedactSensitiveText() throws Exception {
        Path filePath = tempDir.resolve("logs/llm-debug.jsonl");
        LlmDebugProperties properties = new LlmDebugProperties();
        properties.setEnabled(true);
        properties.setFilePath(filePath.toString());
        properties.setRedactEnabled(true);
        LlmDebugJsonlLogger logger = new LlmDebugJsonlLogger(properties);

        logger.logRequest("r1", "task_reason", "openai", "gpt-test",
                PromptLoader.PromptContext.defaultFor("s1", "web"),
                "contact me at a@b.com and 13800138000 id=110101199001011234 authorization=BearerABC",
                List.of(UserMessage.from("apiKey=secret")),
                ToolChoice.AUTO,
                List.of("toolA"));

        String line = Files.readString(filePath);
        assertFalse(line.contains("a@b.com"));
        assertFalse(line.contains("13800138000"));
        assertFalse(line.contains("110101199001011234"));
        assertFalse(line.contains("secret"));
        assertTrue(line.contains("***"));
    }

    @Test
    void shouldTruncateLongField() throws Exception {
        Path filePath = tempDir.resolve("logs/llm-debug.jsonl");
        LlmDebugProperties properties = new LlmDebugProperties();
        properties.setEnabled(true);
        properties.setFilePath(filePath.toString());
        properties.setMaxCharsPerField(64);
        LlmDebugJsonlLogger logger = new LlmDebugJsonlLogger(properties);
        String longText = "x".repeat(500);

        logger.logRequest("r1", "task_reason", "openai", "gpt-test",
                PromptLoader.PromptContext.defaultFor("s1", "web"),
                longText,
                List.of(UserMessage.from("u1")),
                ToolChoice.AUTO,
                List.of("toolA"));

        String line = Files.readAllLines(filePath).getFirst();
        Map<String, Object> payload = JsonUtil.fromJson(line, Map.class);
        List<?> messages = (List<?>) payload.get("messages");
        Map<?, ?> firstMessage = (Map<?, ?>) messages.getFirst();
        String content = String.valueOf(firstMessage.get("content"));
        assertTrue(content.endsWith("...<truncated>"));
    }

    @Test
    void shouldNotThrowWhenWriteFails() throws Exception {
        Path parentFile = tempDir.resolve("as-file");
        Files.writeString(parentFile, "x");
        Path invalidPath = parentFile.resolve("llm-debug.jsonl");
        LlmDebugProperties properties = new LlmDebugProperties();
        properties.setEnabled(true);
        properties.setFilePath(invalidPath.toString());
        LlmDebugJsonlLogger logger = new LlmDebugJsonlLogger(properties);

        assertDoesNotThrow(() -> logger.logRequest("r1", "task_reason", "openai", "gpt-test",
                PromptLoader.PromptContext.defaultFor("s1", "web"),
                "system",
                List.of(UserMessage.from("u1")),
                ToolChoice.AUTO,
                List.of("toolA")));
    }
}
