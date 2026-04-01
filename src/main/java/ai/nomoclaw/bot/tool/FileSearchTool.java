package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitOption;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FileSearchTool implements Tool {

    private static final int MAX_MATCHES = 200;

    @Override
    public String name() {
        return "file_search_tool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        String action = request.args().path("action").asText("");
        return switch (action) {
            case "grep" -> grep(request, start);
            case "glob" -> glob(request, start);
            default -> ToolResult.failure("INVALID_ACTION", "unsupported file search action: " + action, metrics(start));
        };
    }

    private ToolResult grep(ToolRequest request, long start) {
        String query = request.args().path("query").asText("");
        Path root = resolveRoot(request.args().path("path").asText(""), request);
        if (query.isBlank()) {
            return ToolResult.failure("INVALID_ARGS", "query is required", metrics(start));
        }
        if (!Files.exists(root)) {
            return ToolResult.failure("INVALID_ARGS", "path does not exist: " + root, metrics(start));
        }
        ArrayNode matches = JsonNodeFactory.instance.arrayNode();
        StringBuilder output = new StringBuilder();
        AtomicInteger count = new AtomicInteger();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile() || count.get() >= MAX_MATCHES) {
                        return count.get() >= MAX_MATCHES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }
                    String content;
                    try {
                        content = Files.readString(file, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                        return FileVisitResult.CONTINUE;
                    }
                    List<String> lines = List.of(content.split("\\R", -1));
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).contains(query)) {
                            ObjectNode node = JsonNodeFactory.instance.objectNode();
                            node.put("path", file.toAbsolutePath().toString());
                            node.put("line", i + 1);
                            node.put("text", lines.get(i));
                            matches.add(node);
                            output.append(file.toAbsolutePath()).append(':').append(i + 1).append(": ").append(lines.get(i)).append('\n');
                            if (count.incrementAndGet() >= MAX_MATCHES) {
                                return FileVisitResult.TERMINATE;
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.set("matches", matches);
            artifacts.put("count", count.get());
            return ToolResult.success(ToolTextUtils.truncateHead(output.toString().trim()), artifacts, metrics(start));
        } catch (Exception ex) {
            return ToolResult.failure("FILE_SEARCH_ERROR", ex.getMessage(), metrics(start));
        }
    }

    private ToolResult glob(ToolRequest request, long start) {
        String pattern = request.args().path("pattern").asText("");
        Path root = resolveRoot(request.args().path("path").asText(""), request);
        if (pattern.isBlank()) {
            return ToolResult.failure("INVALID_ARGS", "pattern is required", metrics(start));
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        ArrayNode matches = JsonNodeFactory.instance.arrayNode();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(root.relativize(path)) || matcher.matches(path.getFileName()))
                    .limit(MAX_MATCHES)
                    .forEach(path -> matches.add(path.toAbsolutePath().toString()));
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.set("matches", matches);
            artifacts.put("count", matches.size());
            return ToolResult.success(ToolTextUtils.truncateHead(toText(matches)), artifacts, metrics(start));
        } catch (Exception ex) {
            return ToolResult.failure("FILE_SEARCH_ERROR", ex.getMessage(), metrics(start));
        }
    }

    private String toText(ArrayNode matches) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            builder.append(matches.get(i).asText()).append('\n');
        }
        return builder.toString().trim();
    }

    private Path resolveRoot(String path, ToolRequest request) {
        if (path == null || path.isBlank()) {
            return request.agentWorkspacePath();
        }
        return PathResolver.resolveInAgentWorkspace(path, request);
    }

    private ObjectNode metrics(long start) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
