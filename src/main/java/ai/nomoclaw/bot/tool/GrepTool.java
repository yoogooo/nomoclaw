package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

@Component
public class GrepTool implements Tool {

    private static final int DEFAULT_HEAD_LIMIT = 250;

    @Override
    public String name() {
        return "GrepTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        String patternText = request.args().path("pattern").asText("").trim();
        if (patternText.isBlank()) {
            return ToolResult.failure("INVALID_ARGS", "pattern is required", metrics(start));
        }
        String outputMode = normalizeOutputMode(request.args().path("output_mode").asText(""));
        String glob = request.args().path("glob").asText("").trim();
        boolean caseInsensitive = request.args().path("-i").asBoolean(false)
                || request.args().path("case_insensitive").asBoolean(false);
        boolean withLineNumber = !request.args().has("-n") || request.args().path("-n").asBoolean(true);
        int headLimit = parseIntOrDefault(request.args().path("head_limit").asText(""), DEFAULT_HEAD_LIMIT);
        int offset = Math.max(0, parseIntOrDefault(request.args().path("offset").asText(""), 0));
        Path root = resolveRoot(request.args().path("path").asText(""), request);
        if (!Files.exists(root)) {
            return ToolResult.failure("INVALID_ARGS", "path does not exist: " + root, metrics(start));
        }
        if (!Files.isDirectory(root) && !Files.isRegularFile(root)) {
            return ToolResult.failure("INVALID_ARGS", "path is not a file or directory: " + root, metrics(start));
        }

        Pattern pattern;
        Pattern whitespaceTolerantLiteralPattern = null;
        try {
            int flags = (caseInsensitive ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0)
                    | Pattern.UNICODE_CHARACTER_CLASS;
            pattern = Pattern.compile(patternText, flags);
            whitespaceTolerantLiteralPattern = buildWhitespaceTolerantLiteralPattern(patternText, flags);
        } catch (PatternSyntaxException ex) {
            return ToolResult.failure("INVALID_ARGS", "invalid regex pattern: " + ex.getMessage(), metrics(start));
        }

        try {
            SearchOutcome outcome = search(root, pattern, whitespaceTolerantLiteralPattern, glob, outputMode, withLineNumber);
            SliceResult sliceResult = slice(outcome.lines, headLimit, offset);
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("mode", outputMode);
            artifacts.put("numFiles", outcome.matchedFilesCount);
            artifacts.put("numMatches", outcome.matchCount);
            artifacts.put("scannedFiles", outcome.scannedFilesCount);
            artifacts.put("appliedOffset", offset);
            if (sliceResult.appliedLimit > 0) {
                artifacts.put("appliedLimit", sliceResult.appliedLimit);
            }
            ArrayNode filenames = JsonNodeFactory.instance.arrayNode();
            for (String file : outcome.matchedFiles) {
                filenames.add(file);
            }
            artifacts.set("filenames", filenames);
            if ("content".equals(outputMode)) {
                artifacts.put("numLines", outcome.lines.size());
            }

            String output = String.join("\n", sliceResult.items);
            return ToolResult.success(ToolTextUtils.truncateHead(output), artifacts, metrics(start));
        } catch (Exception ex) {
            return ToolResult.failure("FILE_ERROR", ex.getMessage(), metrics(start));
        }
    }

    private SearchOutcome search(Path root,
                                 Pattern pattern,
                                 Pattern whitespaceTolerantLiteralPattern,
                                 String glob,
                                 String outputMode,
                                 boolean withLineNumber) throws Exception {
        PathMatcher matcher = buildMatcher(glob);
        List<String> lines = new ArrayList<>();
        Set<String> matchedFiles = new HashSet<>();
        AtomicInteger matchCount = new AtomicInteger();
        AtomicInteger scannedFiles = new AtomicInteger();

        try (Stream<Path> stream = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(file -> matcher == null || matchesGlob(root, file, matcher))
                    .forEach(file -> {
                        scannedFiles.incrementAndGet();
                        if ("files_with_matches".equals(outputMode)) {
                            if (fileHasMatch(file, pattern, whitespaceTolerantLiteralPattern)) {
                                matchedFiles.add(file.toAbsolutePath().toString());
                            }
                            return;
                        }
                        scanFileLines(root, file, pattern, whitespaceTolerantLiteralPattern, outputMode, withLineNumber, lines, matchedFiles, matchCount);
                    });
        }

        if ("files_with_matches".equals(outputMode)) {
            lines.addAll(matchedFiles.stream().sorted().toList());
            matchCount.set(matchedFiles.size());
        }
        if ("count".equals(outputMode)) {
            lines.clear();
            lines.add("Total matches: " + matchCount.get());
        }
        return new SearchOutcome(lines, matchedFiles.stream().sorted().toList(), matchedFiles.size(), matchCount.get(), scannedFiles.get());
    }

    private void scanFileLines(Path root,
                               Path file,
                               Pattern pattern,
                               Pattern whitespaceTolerantLiteralPattern,
                               String outputMode,
                               boolean withLineNumber,
                               List<String> lines,
                               Set<String> matchedFiles,
                               AtomicInteger matchCount) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                Matcher m = pattern.matcher(line);
                if (!m.find() && !matchesWhitespaceTolerantLiteral(line, whitespaceTolerantLiteralPattern)) {
                    continue;
                }
                matchCount.incrementAndGet();
                matchedFiles.add(file.toAbsolutePath().toString());
                if (!"content".equals(outputMode)) {
                    continue;
                }
                String prefix = withLineNumber
                        ? file.toAbsolutePath() + ":" + lineNo + ": "
                        : file.toAbsolutePath() + ": ";
                lines.add(prefix + line);
            }
        } catch (Exception ignored) {
            // Skip unreadable or binary-like files.
        }
    }

    private boolean fileHasMatch(Path file, Pattern pattern, Pattern whitespaceTolerantLiteralPattern) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (pattern.matcher(line).find() || matchesWhitespaceTolerantLiteral(line, whitespaceTolerantLiteralPattern)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private boolean matchesGlob(Path root, Path file, PathMatcher matcher) {
        Path relative = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        return matcher.matches(relative) || matcher.matches(file.getFileName());
    }

    private PathMatcher buildMatcher(String glob) {
        if (glob == null || glob.isBlank()) {
            return null;
        }
        return FileSystems.getDefault().getPathMatcher("glob:" + glob);
    }

    private SliceResult slice(List<String> all, int limit, int offset) {
        int safeOffset = Math.min(offset, all.size());
        if (limit == 0) {
            return new SliceResult(all.subList(safeOffset, all.size()), 0);
        }
        int safeLimit = limit < 0 ? DEFAULT_HEAD_LIMIT : limit;
        int end = Math.min(all.size(), safeOffset + safeLimit);
        return new SliceResult(all.subList(safeOffset, end), safeLimit);
    }

    private String normalizeOutputMode(String raw) {
        String mode = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if ("content".equals(mode) || "count".equals(mode) || "files_with_matches".equals(mode)) {
            return mode;
        }
        return "files_with_matches";
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Pattern buildWhitespaceTolerantLiteralPattern(String patternText, int flags) {
        if (!isLikelyLiteral(patternText) || !patternText.contains(" ")) {
            return null;
        }
        String[] tokens = patternText.trim().split(" +");
        if (tokens.length < 2) {
            return null;
        }
        String joined = Arrays.stream(tokens)
                .map(Pattern::quote)
                .reduce((left, right) -> left + "[\\s\\u00A0\\u2007\\u202F]+" + right)
                .orElse("");
        if (joined.isBlank()) {
            return null;
        }
        return Pattern.compile(joined, flags);
    }

    private boolean isLikelyLiteral(String text) {
        return !text.matches(".*[.\\\\+*?\\[\\](){}|^$].*");
    }

    private boolean matchesWhitespaceTolerantLiteral(String line, Pattern pattern) {
        return pattern != null && pattern.matcher(line).find();
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

    private record SearchOutcome(List<String> lines,
                                 List<String> matchedFiles,
                                 int matchedFilesCount,
                                 int matchCount,
                                 int scannedFilesCount) {
    }

    private record SliceResult(List<String> items, int appliedLimit) {
    }
}
