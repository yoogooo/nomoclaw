package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.channel.config.ChannelBotCredentialResolver;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CronChannelTargetDirectoryService {

    private static final ObjectMapper MAPPER = JsonUtil.mapper();
    private static final int MAX_LIMIT = 50;

    private final ChannelBotCredentialResolver botCredentialResolver;
    private final HttpClient httpClient;

    private final Map<String, TokenState> feishuTokenCache = new ConcurrentHashMap<>();
    private final Map<String, NameState> feishuBotNameCache = new ConcurrentHashMap<>();

    public CronChannelTargetDirectoryService(ChannelBotCredentialResolver botCredentialResolver,
                                             HttpClient appHttpClient) {
        this.botCredentialResolver = botCredentialResolver;
        this.httpClient = appHttpClient;
    }

    public SearchResult search(String channel, String keyword, String botId, int limit) {
        String normalizedKeyword = trim(keyword);
        String normalizedBotId = trim(botId);
        int safeLimit = Math.clamp(limit, 1, MAX_LIMIT);
        ChannelType channelType = ChannelType.from(channel);
        if (channelType != ChannelType.FEISHU && channelType != ChannelType.DINGTALK) {
            return new SearchResult(List.of(), "unsupported channel");
        }

        List<TargetItem> platform = List.of();
        String error = null;
        boolean robotKeywordMatched;
        if (channelType == ChannelType.FEISHU) {
            try {
                robotKeywordMatched = isFeishuRobotKeyword(normalizedKeyword, normalizedBotId);
                String effectiveKeyword = robotKeywordMatched ? "" : normalizedKeyword;
                platform = searchFeishuTargets(effectiveKeyword, normalizedBotId, safeLimit);
            } catch (Exception ex) {
                error = toReadableSearchError(ex);
                log.warn("[CronTargetDirectory] feishu search failed keyword={} err={}", normalizedKeyword, ex.toString());
            }
        }

        return new SearchResult(platform.stream().limit(safeLimit).toList(), error);
    }

    private List<TargetItem> searchFeishuTargets(String keyword, String botId, int limit) throws Exception {
        String token = feishuTenantAccessToken(botId);
        LinkedHashMap<String, TargetItem> merged = new LinkedHashMap<>();
        for (TargetItem item : searchFeishuChats(token, keyword, limit)) {
            merged.putIfAbsent(item.target(), item);
        }
        if (!keyword.isBlank()) {
            for (TargetItem item : searchFeishuUsers(token, keyword, limit)) {
                merged.putIfAbsent(item.target(), item);
            }
        }
        return merged.values().stream().limit(limit).toList();
    }

    private List<TargetItem> searchFeishuChats(String token, String keyword, int limit) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.feishu.cn/open-apis/im/v1/chats?page_size=50"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=utf-8")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = readFeishuResponse(response, "im/v1/chats");
        ArrayList<TargetItem> items = new ArrayList<>();
        for (JsonNode item : root.path("data").path("items")) {
            String chatId = trim(item.path("chat_id").asString(""));
            if (chatId.isBlank()) {
                continue;
            }
            String name = trim(item.path("name").asString(""));
            String label = name.isBlank() ? chatId : name;
            String target = "feishu:chat_id:" + chatId;
            if (!matchKeyword(keyword, label, target, chatId)) {
                continue;
            }
            items.add(new TargetItem(label, target, "group", "platform"));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private List<TargetItem> searchFeishuUsers(String token, String keyword, int limit) throws Exception {
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String url = "https://open.feishu.cn/open-apis/contact/v3/users/find_by_name?name=" + encodedKeyword + "&page_size=50&user_id_type=open_id";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=utf-8")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = readFeishuResponse(response, "contact/v3/users/find_by_name");
        ArrayList<TargetItem> items = new ArrayList<>();
        for (JsonNode item : root.path("data").path("items")) {
            String openId = trim(item.path("open_id").asString(""));
            String userId = trim(item.path("user_id").asString(""));
            String id = !openId.isBlank() ? openId : userId;
            if (id.isBlank()) {
                continue;
            }
            String name = trim(item.path("name").asString(""));
            String label = name.isBlank() ? id : name;
            String target = !openId.isBlank() ? "feishu:open_id:" + openId : "feishu:user_id:" + userId;
            if (!matchKeyword(keyword, label, target, id)) {
                continue;
            }
            items.add(new TargetItem(label, target, "user", "platform"));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private JsonNode readFeishuResponse(HttpResponse<String> response, String api) {
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("http " + response.statusCode() + " from " + api);
        }
        JsonNode root = MAPPER.readTree(response.body());
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("code=" + code + " msg=" + trim(root.path("msg").asString("")));
        }
        return root;
    }

    private String feishuTenantAccessToken(String botId) throws Exception {
        String normalizedBotId = trim(botId);
        if (normalizedBotId.isBlank()) {
            normalizedBotId = "default";
        }
        Instant now = Instant.now();
        TokenState cached = feishuTokenCache.get(normalizedBotId);
        if (cached != null && now.isBefore(cached.expireAt().minusSeconds(60))) {
            return cached.token();
        }
        ChannelBotCredentialResolver.FeishuBotCredential bot = botCredentialResolver.resolveFeishu(botId);
        if (bot == null) {
            throw new IllegalStateException("missing feishu bot credentials");
        }
        String appId = trim(bot.appId());
        String appSecret = trim(bot.appSecret());
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new IllegalStateException("missing feishu app credentials");
        }
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
        String token = trim(root.path("tenant_access_token").asString(""));
        if (token.isBlank()) {
            throw new IllegalStateException("empty tenant access token");
        }
        int expire = root.path("expire").asInt(7200);
        feishuTokenCache.put(normalizedBotId, new TokenState(token, now.plusSeconds(Math.max(120, expire))));
        return token;
    }

    private boolean isFeishuRobotKeyword(String keyword, String botId) {
        String normalizedKeyword = trim(keyword).toLowerCase(Locale.ROOT);
        if (normalizedKeyword.isBlank()) {
            return false;
        }
        ChannelBotCredentialResolver.FeishuBotCredential bot = botCredentialResolver.resolveFeishu(botId);
        String appId = bot == null ? "" : trim(bot.appId()).toLowerCase(Locale.ROOT);
        if (!appId.isBlank() && appId.contains(normalizedKeyword)) {
            return true;
        }
        try {
            String botName = resolveFeishuBotName(botId).toLowerCase(Locale.ROOT);
            return !botName.isBlank() && botName.contains(normalizedKeyword);
        } catch (Exception ex) {
            log.warn("[CronTargetDirectory] resolve bot name failed err={}", ex.toString());
            return false;
        }
    }

    private String resolveFeishuBotName(String botId) throws Exception {
        String normalizedBotId = trim(botId);
        if (normalizedBotId.isBlank()) {
            normalizedBotId = "default";
        }
        Instant now = Instant.now();
        NameState cached = feishuBotNameCache.get(normalizedBotId);
        if (cached != null && now.isBefore(cached.expireAt().minusSeconds(30))) {
            return cached.name();
        }
        String token = feishuTenantAccessToken(botId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.feishu.cn/open-apis/bot/v3/info"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=utf-8")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = readFeishuResponse(response, "bot/v3/info");
        String name = trim(root.path("bot").path("name").asString(""));
        if (name.isBlank()) {
            name = trim(root.path("data").path("bot").path("name").asString(""));
        }
        feishuBotNameCache.put(normalizedBotId, new NameState(name, now.plusSeconds(600)));
        return name;
    }

    private boolean matchKeyword(String keyword, String... values) {
        String k = trim(keyword).toLowerCase(Locale.ROOT);
        if (k.isBlank()) {
            return true;
        }
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(k)) {
                return true;
            }
        }
        return false;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String toReadableSearchError(Exception ex) {
        String reason = ex == null ? "" : trim(ex.getMessage());
        if (reason.length() > 160) {
            reason = reason.substring(0, 160);
        }
        if (reason.isBlank()) {
            return "feishu directory unavailable";
        }
        return "feishu directory unavailable: " + reason;
    }

    public record TargetItem(String label, String target, String kind, String source) {
    }

    public record SearchResult(List<TargetItem> items, String error) {
    }

    private record TokenState(String token, Instant expireAt) {
    }

    private record NameState(String name, Instant expireAt) {
    }
}
