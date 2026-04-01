package ai.nomoclaw.bot.channel.config;

import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChannelConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final ObjectMapper MAPPER = JsonUtil.mapper();
    private static final String PROPERTY_SOURCE_NAME = "nomoclawChannelConfig";
    private static final String CONFIG_FILE_NAME = "nomoclaw.json";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path rootDir = resolveRootDir(environment);
        Path configFile = rootDir.resolve(CONFIG_FILE_NAME).toAbsolutePath().normalize();
        ObjectNode root = loadOrCreateConfig(configFile);
        ObjectNode channels = ensureChannelsTemplate(root);
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

    private ObjectNode ensureChannelsTemplate(ObjectNode root) {
        ObjectNode channels = root.path("channels") instanceof ObjectNode node
                ? node
                : MAPPER.createObjectNode();
        root.set("channels", channels);

        ObjectNode feishu = channels.path("feishu") instanceof ObjectNode node
                ? node
                : MAPPER.createObjectNode();
        channels.set("feishu", feishu);
        setIfMissing(feishu, "enabled", false);
        setIfMissing(feishu, "requireMention", true);
        setIfMissing(feishu, "allowList", MAPPER.createArrayNode());
        setIfMissing(feishu, "appId", "");
        setIfMissing(feishu, "appSecret", "");
        setIfMissing(feishu, "processingAckReactionEnabled", true);
        setIfMissing(feishu, "processingAckReactionType", "OK");

        ObjectNode dingtalk = channels.path("dingtalk") instanceof ObjectNode node
                ? node
                : MAPPER.createObjectNode();
        channels.set("dingtalk", dingtalk);
        setIfMissing(dingtalk, "enabled", false);
        setIfMissing(dingtalk, "requireMention", true);
        setIfMissing(dingtalk, "allowList", MAPPER.createArrayNode());
        setIfMissing(dingtalk, "clientId", "");
        setIfMissing(dingtalk, "clientSecret", "");
        setIfMissing(dingtalk, "robotCode", "");
        return channels;
    }

    private void setIfMissing(ObjectNode node, String key, boolean value) {
        if (!node.has(key)) {
            node.put(key, value);
        }
    }

    private void setIfMissing(ObjectNode node, String key, String value) {
        if (!node.has(key)) {
            node.put(key, value);
        }
    }

    private void setIfMissing(ObjectNode node, String key, JsonNode value) {
        if (!node.has(key)) {
            node.set(key, value);
        }
    }

    private Map<String, Object> toChannelProperties(ObjectNode channels) {
        ObjectNode feishu = channels.path("feishu") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        ObjectNode dingtalk = channels.path("dingtalk") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        boolean feishuEnabled = feishu.path("enabled").asBoolean(false);
        boolean dingtalkEnabled = dingtalk.path("enabled").asBoolean(false);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent.channels.enabled", feishuEnabled || dingtalkEnabled);
        props.put("agent.channels.processing-ack-enabled", true);
        props.put("agent.channels.processing-ack-text", "正在处理，请稍候...");
        props.put("agent.channels.feishu.enabled", feishuEnabled);
        props.put("agent.channels.feishu.require-mention", feishu.path("requireMention").asBoolean(true));
        props.put("agent.channels.feishu.allow-list", joinList(feishu.path("allowList")));
        props.put("agent.channels.feishu.processing-ack-reaction-enabled", feishu.path("processingAckReactionEnabled").asBoolean(true));
        props.put("agent.channels.feishu.processing-ack-reaction-type", trim(feishu.path("processingAckReactionType").asText("OK")));
        props.put("agent.channels.feishu.app-id", trim(feishu.path("appId").asText("")));
        props.put("agent.channels.feishu.app-secret", trim(feishu.path("appSecret").asText("")));
        props.put("agent.channels.dingtalk.enabled", dingtalkEnabled);
        props.put("agent.channels.dingtalk.require-mention", dingtalk.path("requireMention").asBoolean(true));
        props.put("agent.channels.dingtalk.allow-list", joinList(dingtalk.path("allowList")));
        props.put("agent.channels.dingtalk.client-id", trim(dingtalk.path("clientId").asText("")));
        props.put("agent.channels.dingtalk.client-secret", trim(dingtalk.path("clientSecret").asText("")));
        props.put("agent.channels.dingtalk.robot-code", trim(dingtalk.path("robotCode").asText("")));
        return props;
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
