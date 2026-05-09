package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WebSearchTool implements Tool {

    private static final int MAX_RESULTS = 8;
    private static final Pattern RESULT_LINK_PATTERN = Pattern.compile(
            "<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_WS = Pattern.compile("\\s+");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AgentProperties agentProperties;

    public WebSearchTool(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "WebSearchTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        try {
            String query = request.args().path("query").asString("");
            if (query.isBlank()) {
                return ToolResult.failure("INVALID_ARGS", "query is required", metric(start, 0));
            }
            List<String> allowed = domainList(request.args().get("allowed_domains"));
            List<String> blocked = domainList(request.args().get("blocked_domains"));
            if (!allowed.isEmpty() && !blocked.isEmpty()) {
                return ToolResult.failure("INVALID_ARGS", "cannot specify both allowed_domains and blocked_domains", metric(start, 0));
            }

            List<String> endpoints = configuredEndpoints();
            if (endpoints.isEmpty()) {
                return ToolResult.failure("WEB_SEARCH_ERROR", "no configured search endpoints", metric(start, 0));
            }

            HttpResponse<String> response = null;
            String selectedEndpoint = "";
            List<String> failures = new ArrayList<>();
            for (String endpointTemplate : endpoints) {
                String resolved = buildEndpointUrl(endpointTemplate, query);
                try {
                    URI uri = URI.create(resolved);
                    HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(30))
                            .header("User-Agent", "NomoClaw-WebSearch/1.0")
                            .GET()
                            .build();
                    HttpResponse<String> candidate = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (candidate.statusCode() >= 400) {
                        failures.add(resolved + " -> http " + candidate.statusCode());
                        continue;
                    }
                    response = candidate;
                    selectedEndpoint = resolved;
                    break;
                } catch (Exception ex) {
                    failures.add(resolved + " -> " + ex.getMessage());
                }
            }
            if (response == null) {
                String message = "all search endpoints failed: " + String.join("; ", failures);
                return ToolResult.failure("WEB_SEARCH_ERROR", message, metric(start, 0));
            }

            List<SearchHit> hits = parseHits(response.body(), allowed, blocked);
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("query", query);
            artifacts.put("count", hits.size());
            artifacts.put("endpoint", selectedEndpoint);
            ArrayNode items = JsonNodeFactory.instance.arrayNode();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < hits.size(); i++) {
                SearchHit hit = hits.get(i);
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put("title", hit.title());
                node.put("url", hit.url());
                items.add(node);
                out.append(i + 1).append(". ").append(hit.title()).append('\n').append(hit.url()).append('\n');
            }
            artifacts.set("results", items);
            return ToolResult.success(ToolTextUtils.truncateHead(out.toString().trim()), artifacts, metric(start, hits.size()));
        } catch (Exception ex) {
            log.warn("[Tool][web-search] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
            return ToolResult.failure("WEB_SEARCH_ERROR", ex.getMessage(), metric(start, 0));
        }
    }

    private List<String> domainList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isString()) {
            String raw = node.asString("");
            if (raw.isBlank()) {
                return List.of();
            }
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .toList();
        }
        if (!node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item == null ? "" : item.asString("").trim();
            if (!value.isBlank()) {
                out.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private List<String> configuredEndpoints() {
        List<String> endpoints = agentProperties.getWebSearch().getEndpoints();
        if (endpoints == null) {
            return List.of();
        }
        return endpoints.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());
    }

    private String buildEndpointUrl(String template, String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        if (template.contains("{query}") || template.contains("{rawQuery}")) {
            return template.replace("{query}", encoded).replace("{rawQuery}", query);
        }
        String separator = template.contains("?") ? "&" : "?";
        return template + separator + "q=" + encoded;
    }

    private List<SearchHit> parseHits(String html, List<String> allowedDomains, List<String> blockedDomains) {
        List<SearchHit> hits = new ArrayList<>();
        Matcher matcher = RESULT_LINK_PATTERN.matcher(html == null ? "" : html);
        while (matcher.find() && hits.size() < MAX_RESULTS) {
            String href = decodeDuckDuckGoRedirect(matcher.group(1));
            String title = normalizeText(matcher.group(2));
            if (href.isBlank() || title.isBlank()) {
                continue;
            }
            if (!isHttpUrl(href)) {
                continue;
            }
            String host = hostOf(href).toLowerCase(Locale.ROOT);
            if (!allowedDomains.isEmpty() && allowedDomains.stream().noneMatch(host::endsWith)) {
                continue;
            }
            if (!blockedDomains.isEmpty() && blockedDomains.stream().anyMatch(host::endsWith)) {
                continue;
            }
            hits.add(new SearchHit(title, href));
        }
        return hits;
    }

    private String decodeDuckDuckGoRedirect(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(href);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return href;
            }
            for (String part : query.split("&")) {
                if (part.startsWith("uddg=")) {
                    return URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return href;
    }

    private String normalizeText(String text) {
        String noTags = TAGS.matcher(text == null ? "" : text).replaceAll(" ");
        return MULTI_WS.matcher(noTags).replaceAll(" ").trim();
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String hostOf(String value) {
        try {
            return URI.create(value).getHost() == null ? "" : URI.create(value).getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private ObjectNode metric(long start, int size) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("size", size);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }

    private record SearchHit(String title, String url) {
    }
}
