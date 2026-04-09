package ai.nomoclaw.bot.channel.platform;

import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
public class FeishuBotTargetResolverService {

    private static final ObjectMapper MAPPER = JsonUtil.mapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ResolveResult resolve(String appId, String appSecret) {
        String normalizedAppId = trim(appId);
        String normalizedAppSecret = trim(appSecret);
        if (normalizedAppId.isBlank() || normalizedAppSecret.isBlank()) {
            return ResolveResult.unresolved("missing app credentials");
        }
        try {
            String token = tenantAccessToken(normalizedAppId, normalizedAppSecret);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/bot/v3/info"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = readFeishuResponse(response, "bot/v3/info");
            JsonNode botNode = root.path("bot").isObject() ? root.path("bot") : root.path("data").path("bot");
            String openId = trim(botNode.path("open_id").asText(""));
            if (openId.isBlank()) {
                return ResolveResult.unresolved("open_id missing in bot info response");
            }
            String name = trim(botNode.path("name").asText(""));
            if (name.isBlank()) {
                name = "Feishu Bot";
            }
            return ResolveResult.resolved("feishu:open_id:" + openId, name, Instant.now().toString());
        } catch (Exception ex) {
            log.warn("[FeishuBotTargetResolver] resolve open_id failed appId={} err={}", normalizedAppId, ex.toString());
            return ResolveResult.unresolved(trim(ex.getMessage()));
        }
    }

    private String tenantAccessToken(String appId, String appSecret) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "app_id", appId,
                "app_secret", appSecret
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = readFeishuResponse(response, "auth/v3/tenant_access_token/internal");
        String token = trim(root.path("tenant_access_token").asText(""));
        if (token.isBlank()) {
            throw new IllegalStateException("tenant_access_token is empty");
        }
        return token;
    }

    private JsonNode readFeishuResponse(HttpResponse<String> response, String api) throws Exception {
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("http " + response.statusCode() + " from " + api);
        }
        JsonNode root = MAPPER.readTree(response.body());
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("code=" + code + " msg=" + trim(root.path("msg").asText("")));
        }
        return root;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public record ResolveResult(
            boolean resolved,
            String defaultTarget,
            String defaultTargetDisplayName,
            String resolvedAt,
            String error
    ) {
        public static ResolveResult resolved(String target, String displayName, String resolvedAt) {
            return new ResolveResult(true, target, displayName, resolvedAt, "");
        }

        public static ResolveResult unresolved(String error) {
            return new ResolveResult(false, "", "", "", error == null ? "" : error);
        }
    }
}
