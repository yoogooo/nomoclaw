package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.store.entity.AgentEventEntity;
import ai.nomoclaw.bot.store.entity.AgentMessageAttachmentEntity;
import ai.nomoclaw.bot.store.repository.AgentEventRepository;
import ai.nomoclaw.bot.store.repository.AgentMessageAttachmentRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ImageLoaderTool implements Tool {

    private static final Pattern ROUND_PATTERN = Pattern.compile("第\\s*(\\d+)\\s*轮(?:截图|图|screenshot)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_NAME_PATTERN = Pattern.compile("([^\\s/\\\\]+\\.(png|jpg|jpeg|webp))", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_IMAGE_URL_PATTERN = Pattern.compile("(https?://\\S+)", Pattern.CASE_INSENSITIVE);

    private final AgentEventRepository eventRepository;
    private final AgentMessageAttachmentRepository attachmentRepository;

    public ImageLoaderTool(AgentEventRepository eventRepository,
                           AgentMessageAttachmentRepository attachmentRepository) {
        this.eventRepository = eventRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @Override
    public String name() {
        return "ImageLoaderTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        String reference = request.args().path("reference").asString("").trim();
        if (reference.isBlank()) {
            return ToolResult.failure("INVALID_ARGS", "reference is required", metrics(start, false, 0));
        }
        int maxImages = clampMaxImages(request.args().path("maxImages").asInt(1));
        List<AgentEventEntity> events = eventRepository.listByConversationUid(request.conversationUid());
        Map<String, Integer> conversationRoundByKey = buildConversationRoundMap(events);
        List<CandidateImage> candidates = collectCandidates(request.conversationUid(), events, conversationRoundByKey);
        ReferenceType referenceType = detectReferenceType(reference);
        List<CandidateImage> resolved = resolve(reference, referenceType, maxImages, candidates);

        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("reference", reference);
        artifacts.put("referenceType", referenceType.value);
        artifacts.put("matched", !resolved.isEmpty());
        artifacts.put("resolvedCount", resolved.size());
        ArrayNode images = artifacts.putArray("images");
        for (CandidateImage image : resolved) {
            ObjectNode node = images.addObject();
            node.put("path", image.location());
            node.put("name", image.name());
            node.put("sourceMessageUid", image.sourceMessageUid());
            node.put("sourceStepUid", image.sourceStepUid());
            node.put("roundIndex", image.roundIndex());
            node.put("conversationRoundIndex", image.conversationRoundIndex());
            node.put("isExternal", image.external());
            if (image.external()) {
                node.put("url", image.location());
            }
        }

        String output = resolved.isEmpty()
                ? "no matched images found in current conversation"
                : "resolved " + resolved.size() + " image(s): "
                + resolved.stream().map(CandidateImage::name).collect(Collectors.joining(", "));
        return ToolResult.success(output, artifacts, metrics(start, true, resolved.size()));
    }

    private ReferenceType detectReferenceType(String reference) {
        if (reference == null || reference.isBlank()) {
            return ReferenceType.UNKNOWN;
        }
        if (ROUND_PATTERN.matcher(reference).find()) {
            return ReferenceType.ROUND;
        }
        if (extractHttpImageUrl(reference) != null) {
            return ReferenceType.URL;
        }
        if (extractImageFileName(reference) != null) {
            return ReferenceType.FILE_NAME;
        }
        String normalized = reference.toLowerCase(Locale.ROOT);
        if (normalized.contains("刚才截图")
                || normalized.contains("最近截图")
                || normalized.contains("latest screenshot")
                || normalized.contains("last screenshot")
                || normalized.contains("screenshot")
                || normalized.contains("截图")) {
            return ReferenceType.LATEST;
        }
        return ReferenceType.UNKNOWN;
    }

    private List<CandidateImage> resolve(String reference,
                                         ReferenceType referenceType,
                                         int maxImages,
                                         List<CandidateImage> candidates) {
        if (referenceType == ReferenceType.URL) {
            String url = extractHttpImageUrl(reference);
            if (url == null || url.isBlank()) {
                return List.of();
            }
            return List.of(new CandidateImage(
                    url,
                    deriveNameFromUrl(url),
                    "",
                    "",
                    0,
                    0,
                    false,
                    Instant.now(),
                    true
            ));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        Comparator<CandidateImage> descByTs = Comparator.comparing(CandidateImage::timestamp).reversed();
        return switch (referenceType) {
            case LATEST -> candidates.stream()
                    .filter(CandidateImage::screenshot)
                    .sorted(descByTs)
                    .limit(maxImages)
                    .toList();
            case ROUND -> {
                Matcher matcher = ROUND_PATTERN.matcher(reference);
                if (!matcher.find()) {
                    yield List.of();
                }
                int requestedRound = parseIntSafe(matcher.group(1));
                if (requestedRound <= 0) {
                    yield List.of();
                }
                yield candidates.stream()
                        .filter(CandidateImage::screenshot)
                        .filter(item -> item.conversationRoundIndex() == requestedRound)
                        .sorted(descByTs)
                        .limit(maxImages)
                        .toList();
            }
            case FILE_NAME -> {
                String fileName = extractImageFileName(reference);
                if (fileName == null || fileName.isBlank()) {
                    yield List.of();
                }
                String normalizedFileName = fileName.toLowerCase(Locale.ROOT);
                List<CandidateImage> exact = candidates.stream()
                        .filter(item -> item.name().equalsIgnoreCase(fileName))
                        .sorted(descByTs)
                        .toList();
                if (!exact.isEmpty()) {
                    yield exact.stream().limit(maxImages).toList();
                }
                yield candidates.stream()
                        .filter(item -> item.name().toLowerCase(Locale.ROOT).contains(normalizedFileName))
                        .sorted(descByTs)
                        .limit(maxImages)
                        .toList();
            }
            case UNKNOWN -> List.of();
            case URL -> List.of();
        };
    }

    private List<CandidateImage> collectCandidates(String conversationUid,
                                                   List<AgentEventEntity> events,
                                                   Map<String, Integer> conversationRoundByKey) {
        Map<String, CandidateImage> byPath = new LinkedHashMap<>();
        for (AgentEventEntity event : events) {
            if (event == null || !"STEP_FINISHED".equalsIgnoreCase(nullToEmpty(event.getEventType()))) {
                continue;
            }
            JsonNode payload = parsePayload(event.getPayload());
            if (payload == null || !payload.path("success").asBoolean(false)) {
                continue;
            }
            String toolName = payload.path("toolName").asString("");
            boolean isScreenshot = isScreenshotStep(toolName, payload.path("toolArgs"));
            String pathText = payload.path("artifacts").path("path").asString("");
            Path imagePath = resolveLocalImagePath(pathText);
            if (imagePath == null) {
                continue;
            }
            int roundIndex = extractRoundIndex(payload);
            String key = roundKey(event.getMessageUid(), roundIndex);
            CandidateImage candidate = new CandidateImage(
                    imagePath.toString(),
                    fileName(imagePath),
                    nullToEmpty(event.getMessageUid()),
                    nullToEmpty(event.getStepUid()),
                    roundIndex,
                    conversationRoundByKey.getOrDefault(key, 0),
                    isScreenshot,
                    toInstant(event.getCreatedTime()),
                    false
            );
            byPath.putIfAbsent(imagePath.toString(), candidate);
        }

        for (AgentMessageAttachmentEntity attachment : attachmentRepository.listActiveByConversationAndMimeGroup(conversationUid, "image")) {
            if (attachment == null) {
                continue;
            }
            Path imagePath;
            try {
                imagePath = Path.of(nullToEmpty(attachment.getFilePath())).toAbsolutePath().normalize();
            } catch (Exception ignored) {
                continue;
            }
            if (!Files.isRegularFile(imagePath) || !isSupportedImage(imagePath)) {
                continue;
            }
            byPath.putIfAbsent(imagePath.toString(), new CandidateImage(
                    imagePath.toString(),
                    fileName(imagePath),
                    nullToEmpty(attachment.getMessageUid()),
                    "",
                    0,
                    0,
                    false,
                    toInstant(attachment.getCreatedTime()),
                    false
            ));
        }
        return new ArrayList<>(byPath.values());
    }

    private boolean isScreenshotStep(String toolName, JsonNode toolArgs) {
        String normalizedTool = toolName == null ? "" : toolName.trim();
        if ("DesktopScreenshotTool".equals(normalizedTool)) {
            return true;
        }
        if (!"BrowserTool".equals(normalizedTool)) {
            return false;
        }
        return "screenshot".equals(nullToEmpty(toolArgs.path("action").asString("")));
    }

    private Path resolveLocalImagePath(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return null;
        }
        try {
            Path imagePath = Path.of(pathText).toAbsolutePath().normalize();
            if (!Files.isRegularFile(imagePath) || !isSupportedImage(imagePath)) {
                return null;
            }
            return imagePath;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Integer> buildConversationRoundMap(List<AgentEventEntity> events) {
        LinkedHashMap<String, Integer> rounds = new LinkedHashMap<>();
        for (AgentEventEntity event : events) {
            if (event == null || event.getMessageUid() == null || event.getMessageUid().isBlank()) {
                continue;
            }
            JsonNode payload = parsePayload(event.getPayload());
            int roundIndex = extractRoundIndex(payload);
            if (roundIndex <= 0) {
                continue;
            }
            String key = roundKey(event.getMessageUid(), roundIndex);
            rounds.computeIfAbsent(key, ignored -> rounds.size() + 1);
        }
        return rounds;
    }

    private int extractRoundIndex(JsonNode payload) {
        if (payload == null) {
            return 0;
        }
        int roundIndex = payload.path("roundIndex").asInt(0);
        if (roundIndex > 0) {
            return roundIndex;
        }
        return payload.path("round").asInt(0);
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JsonUtil.fromJson(payload, JsonNode.class);
            return node == null || !node.isObject() ? null : node;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractImageFileName(String reference) {
        Matcher matcher = IMAGE_NAME_PATTERN.matcher(reference == null ? "" : reference.trim());
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        return value == null ? null : value.trim();
    }

    private String extractHttpImageUrl(String reference) {
        Matcher matcher = HTTP_IMAGE_URL_PATTERN.matcher(reference == null ? "" : reference.trim());
        while (matcher.find()) {
            String candidate = sanitizeUrlTail(matcher.group(1));
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private String sanitizeUrlTail(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        while (!trimmed.isEmpty()) {
            char c = trimmed.charAt(trimmed.length() - 1);
            if (c == ',' || c == ';' || c == ')' || c == ']' || c == '}' || c == '"' || c == '\'') {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
                continue;
            }
            break;
        }
        return trimmed;
    }

    private String deriveNameFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "remote-image";
        }
        int idx = url.lastIndexOf('/');
        String segment = idx >= 0 && idx < url.length() - 1 ? url.substring(idx + 1) : "remote-image";
        int queryIdx = segment.indexOf('?');
        if (queryIdx >= 0) {
            segment = segment.substring(0, queryIdx);
        }
        return segment.isBlank() ? "remote-image" : segment;
    }

    private int clampMaxImages(int value) {
        if (value <= 0) {
            return 1;
        }
        return Math.min(value, 3);
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String fileName(Path path) {
        Path fileName = path == null ? null : path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private boolean isSupportedImage(Path file) {
        String name = fileName(file).toLowerCase(Locale.ROOT);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp");
    }

    private String roundKey(String messageUid, int roundIndex) {
        return nullToEmpty(messageUid) + "#" + roundIndex;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return Instant.EPOCH;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private ObjectNode metrics(long start, boolean success, int resolvedCount) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("success", success);
        metrics.put("resolvedCount", Math.max(0, resolvedCount));
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }

    private enum ReferenceType {
        LATEST("latest"),
        ROUND("round"),
        FILE_NAME("filename"),
        URL("url"),
        UNKNOWN("unknown");

        private final String value;

        ReferenceType(String value) {
            this.value = value;
        }
    }

    private record CandidateImage(String location,
                                  String name,
                                  String sourceMessageUid,
                                  String sourceStepUid,
                                  int roundIndex,
                                  int conversationRoundIndex,
                                  boolean screenshot,
                                  Instant timestamp,
                                  boolean external) {
    }
}
