package ai.nomoclaw.bot.llm.debug;

import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileLlmTraceStoreTests {

    @TempDir
    Path tempDir;

    private final Path originalRoot = NomoClawPaths.root();

    @AfterEach
    void restoreRoot() {
        NomoClawPaths.configureRoot(originalRoot);
    }

    @Test
    void savesReadsAndListsTracesForOneMessage() {
        NomoClawPaths.configureRoot(tempDir);
        FileLlmTraceStore store = new FileLlmTraceStore();

        LlmTraceRecord later = trace("trace-later", "message-1", LocalDateTime.of(2026, 1, 1, 10, 1));
        LlmTraceRecord earlier = trace("trace-earlier", "message-1", LocalDateTime.of(2026, 1, 1, 10, 0));
        store.save(later);
        store.save(earlier);
        store.save(trace("trace-other", "message-2", LocalDateTime.of(2026, 1, 1, 10, 2)));

        assertThat(store.find("conversation-1", "trace-earlier").getRequestUid()).isEqualTo("request-trace-earlier");
        assertThat(store.listByMessageUid("conversation-1", "message-1"))
                .extracting(LlmTraceRecord::getTraceUid)
                .containsExactly("trace-earlier", "trace-later");
        assertThat(tempDir.resolve("traces/conversation-conversation-1/msg-message-1/trace-trace-earlier.json")).exists();
    }

    @Test
    void ignoresUnreadableTraceFilesAndRejectsUnsafeIdentifiers() throws Exception {
        NomoClawPaths.configureRoot(tempDir);
        FileLlmTraceStore store = new FileLlmTraceStore();
        Path directory = tempDir.resolve("traces/conversation-conversation-1/msg-message-1");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("broken.json"), "not-json", StandardCharsets.UTF_8);

        assertThat(store.listByMessageUid("conversation-1", "message-1")).isEmpty();
        assertThat(store.find("conversation-1", "../outside")).isNull();
    }

    private LlmTraceRecord trace(String traceUid, String messageUid, LocalDateTime startedAt) {
        LlmTraceRecord trace = new LlmTraceRecord();
        trace.setTraceUid(traceUid);
        trace.setConversationUid("conversation-1");
        trace.setMessageUid(messageUid);
        trace.setRequestUid("request-" + traceUid);
        trace.setRequestStartedTime(startedAt);
        trace.setStatus("REQUESTED");
        return trace;
    }
}
