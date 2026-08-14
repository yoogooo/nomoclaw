package ai.nomoclaw.bot.llm.debug;

import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Stores one complete trace document per file under the local NomoClaw root.
 */
@Component
@Slf4j
public class FileLlmTraceStore implements LlmTraceStore {

    private static final String TRACE_DIRECTORY = "traces";
    private static final String TRACE_SUFFIX = ".json";
    private static final String SAFE_IDENTIFIER = "[A-Za-z0-9_-]+";

    private final ConcurrentMap<String, Object> writeLocks = new ConcurrentHashMap<>();

    @Override
    public void save(LlmTraceRecord trace) {
        if (trace == null) {
            return;
        }
        Path file = tracePath(trace.getConversationUid(), trace.getMessageUid(), trace.getTraceUid());
        synchronized (writeLocks.computeIfAbsent(file.toString(), ignored -> new Object())) {
            try {
                Files.createDirectories(file.getParent());
                Path temporary = Files.createTempFile(file.getParent(), trace.getTraceUid() + ".", ".tmp");
                try {
                    Files.writeString(temporary, JsonUtil.toJson(trace), StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    moveAtomically(temporary, file);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (Exception ex) {
                log.warn("[LlmTrace] failed to write trace file={} err={}", file, ex.toString());
            }
        }
    }

    @Override
    public LlmTraceRecord find(String conversationUid, String traceUid) {
        Path file;
        try {
            file = findTraceFile(conversationUid, traceUid);
        } catch (Exception ex) {
            return null;
        }
        if (file == null || Files.notExists(file)) {
            return null;
        }
        try {
            return JsonUtil.fromJson(Files.readString(file, StandardCharsets.UTF_8), LlmTraceRecord.class);
        } catch (Exception ex) {
            log.warn("[LlmTrace] failed to read trace file={} err={}", file, ex.toString());
            return null;
        }
    }

    @Override
    public List<LlmTraceRecord> listByMessageUid(String conversationUid, String messageUid) {
        Path directory;
        try {
            directory = messageDirectory(conversationUid, messageUid);
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(TRACE_SUFFIX))
                    .map(this::readQuietly)
                    .filter(trace -> trace != null)
                    .sorted(Comparator.comparing(LlmTraceRecord::getRequestStartedTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        } catch (Exception ex) {
            log.warn("[LlmTrace] failed to list trace directory={} err={}", directory, ex.toString());
            return List.of();
        }
    }

    private LlmTraceRecord readQuietly(Path file) {
        try {
            return JsonUtil.fromJson(Files.readString(file, StandardCharsets.UTF_8), LlmTraceRecord.class);
        } catch (Exception ex) {
            log.warn("[LlmTrace] ignored unreadable trace file={} err={}", file, ex.toString());
            return null;
        }
    }

    private Path tracePath(String conversationUid, String messageUid, String traceUid) {
        Path directory = messageDirectory(conversationUid, messageUid);
        String identifier = requiredIdentifier(traceUid, "traceUid");
        Path file = directory.resolve("trace-" + identifier + TRACE_SUFFIX).toAbsolutePath().normalize();
        if (!file.startsWith(directory)) {
            throw new IllegalArgumentException("trace path is outside the trace root");
        }
        return file;
    }

    private Path conversationDirectory(String conversationUid) {
        String identifier = requiredIdentifier(conversationUid, "conversationUid");
        Path root = NomoClawPaths.root().resolve(TRACE_DIRECTORY).toAbsolutePath().normalize();
        Path directory = root.resolve("conversation-" + identifier).toAbsolutePath().normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("conversation path is outside the trace root");
        }
        return directory;
    }

    private Path messageDirectory(String conversationUid, String messageUid) {
        Path conversationDirectory = conversationDirectory(conversationUid);
        String identifier = requiredIdentifier(messageUid, "messageUid");
        Path directory = conversationDirectory.resolve("msg-" + identifier).toAbsolutePath().normalize();
        if (!directory.startsWith(conversationDirectory)) {
            throw new IllegalArgumentException("message path is outside the conversation directory");
        }
        return directory;
    }

    private Path findTraceFile(String conversationUid, String traceUid) throws IOException {
        Path directory = conversationDirectory(conversationUid);
        if (Files.notExists(directory)) {
            return null;
        }
        String fileName = "trace-" + requiredIdentifier(traceUid, "traceUid") + TRACE_SUFFIX;
        try (Stream<Path> paths = Files.find(directory, 2,
                (path, attributes) -> attributes.isRegularFile() && fileName.equals(path.getFileName().toString()))) {
            return paths.findFirst().orElse(null);
        }
    }

    private String requiredIdentifier(String value, String name) {
        if (value == null || !value.matches(SAFE_IDENTIFIER)) {
            throw new IllegalArgumentException(name + " must contain only letters, digits, '_' or '-'");
        }
        return value;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
