package ai.nomoclaw.bot.knowledge.vector;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant REST implementation with metadata filtering.
 */
@Component
public class QdrantVectorStore implements VectorStore {
    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final KnowledgeProperties properties;
    private final HttpClient httpClient;

    public QdrantVectorStore(KnowledgeProperties properties, HttpClient appHttpClient) {
        this.properties = properties;
        this.httpClient = appHttpClient;
    }

    @Override
    public void ensureCollection(String collection, int dimension) {
        request("PUT", "/collections/" + collection, Map.of("vectors", Map.of("size", dimension, "distance", "Cosine")), true);
    }

    @Override
    public void upsert(String collection, List<Point> points) {
        request("PUT", "/collections/" + collection + "/points?wait=true", Map.of("points", points), false);
    }

    @Override
    public void deleteByDocument(String collection, String documentUid) {
        request("POST", "/collections/" + collection + "/points/delete?wait=true", Map.of("filter", match("documentUid", documentUid)), false);
    }

    @Override
    public List<Hit> search(String collection, List<Float> vector, List<String> knowledgeBaseUids, int limit) {
        long started = System.nanoTime();
        List<Map<String, Object>> should = knowledgeBaseUids.stream().map(uid -> Map.of("key", "knowledgeBaseUid", "match", Map.of("value", uid))).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", vector);
        body.put("limit", limit);
        body.put("with_payload", true);
        body.put("filter", Map.of("must", List.of(Map.of("key", "enabled", "match", Map.of("value", true))), "should", should));
        JsonNode root = request("POST", "/collections/" + collection + "/points/query", body, false);
        List<Hit> hits = new ArrayList<>();
        JsonNode result = root.path("result").path("points");
        if (result.isMissingNode()) result = root.path("result");
        for (JsonNode point : result) {
            Map<String, Object> payload = JsonUtil.mapper().convertValue(point.path("payload"), new TypeReference<Map<String, Object>>() {
            });
            hits.add(new Hit(point.path("id").asText(), point.path("score").asDouble(), payload));
        }
        log.info("[KnowledgeSearch][Qdrant] collection={} vectorDimension={} knowledgeBaseCount={} limit={} candidateCount={} scores={} costMs={}",
                collection, vector.size(), knowledgeBaseUids.size(), limit, hits.size(), hits.stream().map(Hit::score).toList(), elapsedMillis(started));
        return hits;
    }

    @Override
    public boolean available() {
        try {
            request("GET", "/collections", null, false);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Map<String, Object> match(String key, String value) {
        return Map.of("must", List.of(Map.of("key", key, "match", Map.of("value", value))));
    }

    private JsonNode request(String method, String path, Object body, boolean allowExists) {
        try {
            KnowledgeProperties.Qdrant config = properties.getVector().getQdrant();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getUrl().replaceAll("/+$", "") + path)).timeout(config.getTimeout()).header("Content-Type", "application/json");
            if (!config.getApiKey().isBlank()) builder.header("api-key", config.getApiKey());
            if ("GET".equals(method)) builder.GET();
            else builder.method(method, HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(body)));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 && !(allowExists && response.statusCode() == 409)) {
                log.warn("[KnowledgeSearch][Qdrant] requestFailed method={} path={} status={}", method, path, response.statusCode());
                throw new IllegalStateException("Qdrant HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body().isBlank() ? JsonUtil.mapper().createObjectNode() : JsonUtil.mapper().readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant request interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Qdrant request failed: " + ex.getMessage(), ex);
        }
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
