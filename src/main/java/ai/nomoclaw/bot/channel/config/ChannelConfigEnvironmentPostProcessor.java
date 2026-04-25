package ai.nomoclaw.bot.channel.config;

import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChannelConfigEnvironmentPostProcessor implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private static final ObjectMapper MAPPER = JsonUtil.mapper();
    private static final String PROPERTY_SOURCE_NAME = "nomoclawChannelConfig";
    private static final String CONFIG_FILE_NAME = "nomoclaw.json";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Path rootDir = resolveRootDir(environment);
        Path configFile = rootDir.resolve(CONFIG_FILE_NAME).toAbsolutePath().normalize();
        ObjectNode root = loadOrCreateConfig(configFile);
        ObjectNode channels = normalizeChannels(root);
        writeConfig(configFile, root);
        Map<String, Object> mapped = toChannelProperties(channels);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, mapped));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private Path resolveRootDir(ConfigurableEnvironment environment) {
        String configured = trim(environment.getProperty("nomoclaw.root-dir"));
        if (!configured.isBlank()) {
            return Path.of(configured);
        }
        String envValue = trim(System.getenv("NOMOCLAW_ROOT_DIR"));
        if (!envValue.isBlank()) {
            return Path.of(envValue);
        }
        return Path.of(System.getProperty("user.home"), NomoClawPaths.ROOT_DIR_NAME);
    }

    private ObjectNode loadOrCreateConfig(Path file) {
        try {
            if (Files.exists(file)) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    JsonNode node = MAPPER.readTree(content);
                    if (node instanceof ObjectNode objectNode) {
                        return objectNode;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return MAPPER.createObjectNode();
    }

    private ObjectNode normalizeChannels(ObjectNode root) {
        ObjectNode channels = root.path("channels") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        root.set("channels", channels);
        channels.set("feishu", normalizeFeishu(channels.path("feishu")));
        channels.set("dingtalk", normalizeDingtalk(channels.path("dingtalk")));
        return channels;
    }

    private ObjectNode normalizeFeishu(JsonNode node) {
        ObjectNode feishu = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        feishu.remove("added");
        boolean enabled = feishu.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode oldBots = feishu.path("bots");
        if (oldBots.isArray() && !oldBots.isEmpty()) {
            oldBots.forEach(bots::add);
        } else {
            ObjectNode defaultBot = MAPPER.createObjectNode();
            defaultBot.put("botId", "default");
            defaultBot.put("displayName", "Feishu Default");
            defaultBot.put("enabled", enabled);
            defaultBot.put("isDefault", true);
            defaultBot.put("requireMention", feishu.path("requireMention").asBoolean(true));
            defaultBot.set("allowList", feishu.path("allowList").isArray() ? feishu.path("allowList") : MAPPER.createArrayNode());
            defaultBot.put("appId", trim(feishu.path("appId").asText("")));
            defaultBot.put("appSecret", trim(feishu.path("appSecret").asText("")));
            defaultBot.put("processingAckReactionEnabled", feishu.path("processingAckReactionEnabled").asBoolean(true));
            defaultBot.put("processingAckReactionType", fallback(trim(feishu.path("processingAckReactionType").asText("")), "OK"));
            bots.add(defaultBot);
        }
        feishu.removeAll();
        feishu.put("enabled", enabled);
        feishu.set("bots", bots);
        return feishu;
    }

    private ObjectNode normalizeDingtalk(JsonNode node) {
        ObjectNode dingtalk = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        dingtalk.remove("added");
        boolean enabled = dingtalk.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode oldBots = dingtalk.path("bots");
        if (oldBots.isArray() && !oldBots.isEmpty()) {
            oldBots.forEach(bots::add);
        } else {
            ObjectNode defaultBot = MAPPER.createObjectNode();
            defaultBot.put("botId", "default");
            defaultBot.put("displayName", "DingTalk Default");
            defaultBot.put("enabled", enabled);
            defaultBot.put("isDefault", true);
            defaultBot.put("requireMention", dingtalk.path("requireMention").asBoolean(true));
            defaultBot.set("allowList", dingtalk.path("allowList").isArray() ? dingtalk.path("allowList") : MAPPER.createArrayNode());
            defaultBot.put("clientId", trim(dingtalk.path("clientId").asText("")));
            defaultBot.put("clientSecret", trim(dingtalk.path("clientSecret").asText("")));
            defaultBot.put("robotCode", trim(dingtalk.path("robotCode").asText("")));
            bots.add(defaultBot);
        }
        dingtalk.removeAll();
        dingtalk.put("enabled", enabled);
        dingtalk.set("bots", bots);
        return dingtalk;
    }

    private Map<String, Object> toChannelProperties(ObjectNode channels) {
        ObjectNode feishu = channels.path("feishu") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        ObjectNode dingtalk = channels.path("dingtalk") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        boolean feishuEnabled = feishu.path("enabled").asBoolean(false);
        boolean dingtalkEnabled = dingtalk.path("enabled").asBoolean(false);

        ObjectNode feishuDefaultBot = pickDefaultEnabledBot(feishu.path("bots"));
        ObjectNode dingtalkDefaultBot = pickDefaultEnabledBot(dingtalk.path("bots"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent.channels.enabled", feishuEnabled || dingtalkEnabled);
        props.put("agent.channels.processing-ack-enabled", true);
        props.put("agent.channels.processing-ack-text", "正在处理，请稍候...");
        props.put("agent.channels.feishu.enabled", feishuEnabled && feishuDefaultBot != null);
        props.put("agent.channels.feishu.require-mention", feishuDefaultBot != null && feishuDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.feishu.allow-list", feishuDefaultBot == null ? "" : joinList(feishuDefaultBot.path("allowList")));
        props.put("agent.channels.feishu.processing-ack-reaction-enabled",
                feishuDefaultBot != null && feishuDefaultBot.path("processingAckReactionEnabled").asBoolean(true));
        props.put("agent.channels.feishu.processing-ack-reaction-type",
                feishuDefaultBot == null ? "OK" : fallback(trim(feishuDefaultBot.path("processingAckReactionType").asText("")), "OK"));
        props.put("agent.channels.feishu.app-id", feishuDefaultBot == null ? "" : trim(feishuDefaultBot.path("appId").asText("")));
        props.put("agent.channels.feishu.app-secret", feishuDefaultBot == null ? "" : trim(feishuDefaultBot.path("appSecret").asText("")));
        props.put("agent.channels.dingtalk.enabled", dingtalkEnabled && dingtalkDefaultBot != null);
        props.put("agent.channels.dingtalk.require-mention", dingtalkDefaultBot != null && dingtalkDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.dingtalk.allow-list", dingtalkDefaultBot == null ? "" : joinList(dingtalkDefaultBot.path("allowList")));
        props.put("agent.channels.dingtalk.client-id", dingtalkDefaultBot == null ? "" : trim(dingtalkDefaultBot.path("clientId").asText("")));
        props.put("agent.channels.dingtalk.client-secret", dingtalkDefaultBot == null ? "" : trim(dingtalkDefaultBot.path("clientSecret").asText("")));
        props.put("agent.channels.dingtalk.robot-code", dingtalkDefaultBot == null ? "" : trim(dingtalkDefaultBot.path("robotCode").asText("")));
        return props;
    }

    private ObjectNode pickDefaultEnabledBot(JsonNode botsNode) {
        if (!(botsNode instanceof ArrayNode bots) || bots.isEmpty()) {
            return null;
        }
        ObjectNode defaultBot = null;
        ObjectNode firstEnabled = null;
        for (JsonNode item : bots) {
            if (!(item instanceof ObjectNode bot)) {
                continue;
            }
            boolean enabled = bot.path("enabled").asBoolean(false);
            if (!enabled) {
                continue;
            }
            if (firstEnabled == null) {
                firstEnabled = bot;
            }
            if (bot.path("isDefault").asBoolean(false)) {
                defaultBot = bot;
                break;
            }
        }
        return defaultBot != null ? defaultBot : firstEnabled;
    }

    private String joinList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        node.forEach(item -> {
            String value = trim(item.asText(""));
            if (value.isBlank()) {
                return;
            }
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(value);
        });
        return builder.toString();
    }

    private void writeConfig(Path file, ObjectNode root) {
        try {
            Files.createDirectories(file.getParent());
            String content = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to init channel config file: " + file, ex);
        }
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
