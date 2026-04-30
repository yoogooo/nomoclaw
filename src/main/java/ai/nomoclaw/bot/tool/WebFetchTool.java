package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Slf4j
public class WebFetchTool implements Tool {

    private static final int MAX_OUTPUT_CHARS = 60_000;
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_WS = Pattern.compile("\\s+");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String name() {
        return "WebFetchTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String url = request.args().path("url").asText("");
            String prompt = request.args().path("prompt").asText("");
            if (url.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "url is required", metric(start, 0));
            }
            URI uri;
            try {
                uri = URI.create(url);
            } catch (Exception ex) {
                return ToolResult.failure("INVALID_ARGS", "invalid url: " + url, metric(start, 0));
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return ToolResult.failure("INVALID_ARGS", "url scheme must be http or https", metric(start, 0));
            }

            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "NomoClaw-WebFetch/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();
            String contentType = response.headers().firstValue("content-type").orElse("");
            String codeText = response.headers().firstValue(":status-text").orElse("");
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            String body = new String(bytes, StandardCharsets.UTF_8);

            String text = normalizeBody(body, contentType);
            String summarized = applyPrompt(prompt, text);
            String output = ToolTextUtils.truncateHead(summarized);
            if (output.length() > MAX_OUTPUT_CHARS) {
                output = output.substring(0, MAX_OUTPUT_CHARS);
            }

            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("url", uri.toString());
            artifacts.put("bytes", bytes.length);
            artifacts.put("code", code);
            artifacts.put("codeText", codeText);
            artifacts.put("contentType", contentType);
            artifacts.put("durationMs", System.currentTimeMillis() - start);
            return ToolResult.success(output, artifacts, metric(start, bytes.length));
        } catch (Exception ex) {
            log.warn("[Tool][web-fetch] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
            return ToolResult.failure("WEB_FETCH_ERROR", ex.getMessage(), metric(start, 0));
        }
    }

    private String normalizeBody(String body, String contentType) {
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedType.contains("html")) {
            String noTags = TAGS.matcher(body).replaceAll(" ");
            return MULTI_WS.matcher(noTags).replaceAll(" ").trim();
        }
        return body == null ? "" : body.trim();
    }

    private String applyPrompt(String prompt, String content) {
        if (prompt == null || prompt.isBlank()) {
            return content;
        }
        if (content == null || content.isBlank()) {
            return "";
        }
        return "Prompt: " + prompt.trim() + "\n\nContent:\n" + content;
    }

    private ObjectNode metric(long start, int size) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("size", size);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
