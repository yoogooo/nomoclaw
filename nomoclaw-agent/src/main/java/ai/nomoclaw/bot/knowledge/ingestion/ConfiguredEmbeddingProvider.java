package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;
import ai.nomoclaw.bot.util.JsonUtil;
import org.springframework.stereotype.Component;
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
 * OpenAI-compatible and Ollama embedding client backed by the model settings screen.
 */
@Component
public class ConfiguredEmbeddingProvider implements EmbeddingProvider {
    private final ModelConfigAppService modelConfigService;
    private final HttpClient httpClient;

    public ConfiguredEmbeddingProvider(ModelConfigAppService modelConfigService, HttpClient appHttpClient) {
        this.modelConfigService = modelConfigService;
        this.httpClient = appHttpClient;
    }

    @Override
    public List<List<Float>> embed(List<String> texts, String providerId, String modelId, int expectedDimension) {
        ModelConfigDto.Provider provider = modelConfigService.getModelConfig().providers().stream()
                .filter(item -> item.id().equals(providerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Embedding provider not found: " + providerId));
        try {
            boolean ollama = provider.local() || "ollama".equals(provider.id());
            String endpoint = trimSlash(provider.baseUrl()) + (ollama ? "/api/embed" : "/embeddings");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelId);
            body.put(ollama ? "input" : "input", texts);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(body)));
            if (!ollama && provider.apiKey() != null && !provider.apiKey().isBlank())
                builder.header("Authorization", "Bearer " + provider.apiKey());
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2)
                throw new IllegalStateException("Embedding API failed: HTTP " + response.statusCode());
            JsonNode root = JsonUtil.mapper().readTree(response.body());
            JsonNode values = ollama ? root.path("embeddings") : root.path("data");
            List<List<Float>> vectors = new ArrayList<>();
            for (JsonNode value : values) {
                JsonNode embedding = ollama ? value : value.path("embedding");
                List<Float> vector = new ArrayList<>();
                for (JsonNode number : embedding) vector.add(number.floatValue());
                if (expectedDimension > 0 && vector.size() != expectedDimension)
                    throw new IllegalStateException("Embedding dimension mismatch: expected " + expectedDimension + ", actual " + vector.size());
                vectors.add(vector);
            }
            if (vectors.size() != texts.size())
                throw new IllegalStateException("Embedding API returned unexpected vector count");
            return vectors;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Embedding request failed: " + ex.getMessage(), ex);
        }
    }

    private String trimSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
