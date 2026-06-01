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
        channels.set("discord", normalizeDiscord(channels.path("discord")));
        channels.set("telegram", normalizeTelegram(channels.path("telegram")));
        channels.set("qq", normalizeQq(channels.path("qq")));
        channels.set("wecom", normalizeWeCom(channels.path("wecom")));
        channels.set("weixin", normalizeWeixin(channels.path("weixin")));
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
            defaultBot.put("appId", trim(feishu.path("appId").asString("")));
            defaultBot.put("appSecret", trim(feishu.path("appSecret").asString("")));
            defaultBot.put("processingAckReactionEnabled", feishu.path("processingAckReactionEnabled").asBoolean(true));
            defaultBot.put("processingAckReactionType", fallback(trim(feishu.path("processingAckReactionType").asString("")), "OK"));
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
            defaultBot.put("clientId", trim(dingtalk.path("clientId").asString("")));
            defaultBot.put("clientSecret", trim(dingtalk.path("clientSecret").asString("")));
            defaultBot.put("robotCode", trim(dingtalk.path("robotCode").asString("")));
            bots.add(defaultBot);
        }
        dingtalk.removeAll();
        dingtalk.put("enabled", enabled);
        dingtalk.set("bots", bots);
        return dingtalk;
    }

    private ObjectNode normalizeDiscord(JsonNode node) {
        ObjectNode discord = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        discord.remove("added");
        boolean enabled = discord.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode oldBots = discord.path("bots");
        if (oldBots.isArray() && !oldBots.isEmpty()) {
            oldBots.forEach(bots::add);
        } else {
            ObjectNode defaultBot = MAPPER.createObjectNode();
            defaultBot.put("botId", "default");
            defaultBot.put("displayName", "Discord Default");
            defaultBot.put("enabled", enabled);
            defaultBot.put("isDefault", true);
            defaultBot.put("requireMention", discord.path("requireMention").asBoolean(true));
            defaultBot.set("allowList", discord.path("allowList").isArray() ? discord.path("allowList") : MAPPER.createArrayNode());
            defaultBot.put("token", trim(discord.path("token").asString("")));
            defaultBot.put("botUserId", trim(discord.path("botUserId").asString("")));
            defaultBot.put("acceptBotMessages", discord.path("acceptBotMessages").asBoolean(false));
            bots.add(defaultBot);
        }
        discord.removeAll();
        discord.put("enabled", enabled);
        discord.set("bots", bots);
        return discord;
    }

    private ObjectNode normalizeTelegram(JsonNode node) {
        ObjectNode telegram = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        telegram.remove("added");
        boolean enabled = telegram.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode oldBots = telegram.path("bots");
        if (oldBots.isArray() && !oldBots.isEmpty()) {
            oldBots.forEach(bots::add);
        } else {
            ObjectNode defaultBot = MAPPER.createObjectNode();
            defaultBot.put("botId", "default");
            defaultBot.put("displayName", "Telegram Default");
            defaultBot.put("enabled", enabled);
            defaultBot.put("isDefault", true);
            defaultBot.put("requireMention", telegram.path("requireMention").asBoolean(true));
            defaultBot.set("allowList", telegram.path("allowList").isArray() ? telegram.path("allowList") : MAPPER.createArrayNode());
            defaultBot.put("token", trim(telegram.path("token").asString("")));
            defaultBot.put("botUsername", normalizeUsername(telegram.path("botUsername").asString("")));
            bots.add(defaultBot);
        }
        telegram.removeAll();
        telegram.put("enabled", enabled);
        telegram.set("bots", bots);
        return telegram;
    }

    private ObjectNode normalizeQq(JsonNode node) {
        ObjectNode qq = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        qq.remove("added");
        boolean enabled = qq.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode oldBots = qq.path("bots");
        if (oldBots.isArray() && !oldBots.isEmpty()) {
            oldBots.forEach(bots::add);
        } else {
            ObjectNode defaultBot = MAPPER.createObjectNode();
            defaultBot.put("botId", "default");
            defaultBot.put("displayName", "QQ Default");
            defaultBot.put("enabled", enabled);
            defaultBot.put("isDefault", true);
            defaultBot.put("requireMention", qq.path("requireMention").asBoolean(true));
            defaultBot.set("allowList", qq.path("allowList").isArray() ? qq.path("allowList") : MAPPER.createArrayNode());
            defaultBot.put("appId", trim(qq.path("appId").asString("")));
            defaultBot.put("clientSecret", trim(qq.path("clientSecret").asString(qq.path("token").asString(""))));
            defaultBot.put("botUserId", trim(qq.path("botUserId").asString("")));
            defaultBot.put("sandbox", qq.path("sandbox").asBoolean(false));
            defaultBot.put("markdownEnabled", qq.path("markdownEnabled").asBoolean(false));
            bots.add(defaultBot);
        }
        qq.removeAll();
        qq.put("enabled", enabled);
        qq.set("bots", bots);
        return qq;
    }

    private ObjectNode normalizeWeCom(JsonNode node) {
        ObjectNode wecom = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        wecom.remove("added");
        boolean enabled = wecom.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode oldBots = wecom.path("bots");
        if (oldBots.isArray() && !oldBots.isEmpty()) {
            oldBots.forEach(bots::add);
        } else {
            ObjectNode defaultBot = MAPPER.createObjectNode();
            defaultBot.put("botId", "default");
            defaultBot.put("displayName", "WeCom Default");
            defaultBot.put("enabled", enabled);
            defaultBot.put("isDefault", true);
            defaultBot.put("requireMention", wecom.path("requireMention").asBoolean(true));
            defaultBot.set("allowList", wecom.path("allowList").isArray() ? wecom.path("allowList") : MAPPER.createArrayNode());
            defaultBot.put("wecomBotId", trim(wecom.path("wecomBotId").asString(wecom.path("botId").asString(""))));
            defaultBot.put("secret", trim(wecom.path("secret").asString("")));
            bots.add(defaultBot);
        }
        wecom.removeAll();
        wecom.put("enabled", enabled);
        wecom.set("bots", bots);
        return wecom;
    }

    private ObjectNode normalizeWeixin(JsonNode node) {
        ObjectNode weixin = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        weixin.remove("added");
        boolean enabled = weixin.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode oldBots = weixin.path("bots");
        if (oldBots.isArray() && !oldBots.isEmpty()) {
            oldBots.forEach(bots::add);
        } else {
            ObjectNode defaultBot = MAPPER.createObjectNode();
            defaultBot.put("botId", "default");
            defaultBot.put("displayName", "Weixin Default");
            defaultBot.put("enabled", enabled);
            defaultBot.put("isDefault", true);
            defaultBot.put("requireMention", weixin.path("requireMention").asBoolean(true));
            defaultBot.set("allowList", weixin.path("allowList").isArray() ? weixin.path("allowList") : MAPPER.createArrayNode());
            defaultBot.put("botToken", trim(weixin.path("botToken").asString("")));
            defaultBot.put("botTokenFile", trim(weixin.path("botTokenFile").asString("")));
            defaultBot.put("baseUrl", fallback(trim(weixin.path("baseUrl").asString("")), "https://ilinkai.weixin.qq.com"));
            bots.add(defaultBot);
        }
        weixin.removeAll();
        weixin.put("enabled", enabled);
        weixin.set("bots", bots);
        return weixin;
    }

    private Map<String, Object> toChannelProperties(ObjectNode channels) {
        ObjectNode feishu = channels.path("feishu") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        ObjectNode dingtalk = channels.path("dingtalk") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        ObjectNode discord = channels.path("discord") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        ObjectNode telegram = channels.path("telegram") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        ObjectNode qq = channels.path("qq") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        ObjectNode wecom = channels.path("wecom") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        ObjectNode weixin = channels.path("weixin") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        boolean feishuEnabled = feishu.path("enabled").asBoolean(false);
        boolean dingtalkEnabled = dingtalk.path("enabled").asBoolean(false);
        boolean discordEnabled = discord.path("enabled").asBoolean(false);
        boolean telegramEnabled = telegram.path("enabled").asBoolean(false);
        boolean qqEnabled = qq.path("enabled").asBoolean(false);
        boolean wecomEnabled = wecom.path("enabled").asBoolean(false);
        boolean weixinEnabled = weixin.path("enabled").asBoolean(false);

        ObjectNode feishuDefaultBot = pickDefaultEnabledBot(feishu.path("bots"));
        ObjectNode dingtalkDefaultBot = pickDefaultEnabledBot(dingtalk.path("bots"));
        ObjectNode discordDefaultBot = pickDefaultEnabledBot(discord.path("bots"));
        ObjectNode telegramDefaultBot = pickDefaultEnabledBot(telegram.path("bots"));
        ObjectNode qqDefaultBot = pickDefaultEnabledBot(qq.path("bots"));
        ObjectNode wecomDefaultBot = pickDefaultEnabledBot(wecom.path("bots"));
        ObjectNode weixinDefaultBot = pickDefaultEnabledBot(weixin.path("bots"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent.channels.enabled", feishuEnabled || dingtalkEnabled || discordEnabled || telegramEnabled
                || qqEnabled || wecomEnabled || weixinEnabled);
        props.put("agent.channels.processing-ack-enabled", true);
        props.put("agent.channels.processing-ack-text", "正在处理，请稍候...");
        props.put("agent.channels.feishu.enabled", feishuEnabled && feishuDefaultBot != null);
        props.put("agent.channels.feishu.require-mention", feishuDefaultBot != null && feishuDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.feishu.allow-list", feishuDefaultBot == null ? "" : joinList(feishuDefaultBot.path("allowList")));
        props.put("agent.channels.feishu.processing-ack-reaction-enabled",
                feishuDefaultBot != null && feishuDefaultBot.path("processingAckReactionEnabled").asBoolean(true));
        props.put("agent.channels.feishu.processing-ack-reaction-type",
                feishuDefaultBot == null ? "OK" : fallback(trim(feishuDefaultBot.path("processingAckReactionType").asString("")), "OK"));
        props.put("agent.channels.feishu.app-id", feishuDefaultBot == null ? "" : trim(feishuDefaultBot.path("appId").asString("")));
        props.put("agent.channels.feishu.app-secret", feishuDefaultBot == null ? "" : trim(feishuDefaultBot.path("appSecret").asString("")));
        props.put("agent.channels.dingtalk.enabled", dingtalkEnabled && dingtalkDefaultBot != null);
        props.put("agent.channels.dingtalk.require-mention", dingtalkDefaultBot != null && dingtalkDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.dingtalk.allow-list", dingtalkDefaultBot == null ? "" : joinList(dingtalkDefaultBot.path("allowList")));
        props.put("agent.channels.dingtalk.client-id", dingtalkDefaultBot == null ? "" : trim(dingtalkDefaultBot.path("clientId").asString("")));
        props.put("agent.channels.dingtalk.client-secret", dingtalkDefaultBot == null ? "" : trim(dingtalkDefaultBot.path("clientSecret").asString("")));
        props.put("agent.channels.dingtalk.robot-code", dingtalkDefaultBot == null ? "" : trim(dingtalkDefaultBot.path("robotCode").asString("")));
        props.put("agent.channels.discord.enabled", discordEnabled && discordDefaultBot != null);
        props.put("agent.channels.discord.require-mention", discordDefaultBot != null && discordDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.discord.allow-list", discordDefaultBot == null ? "" : joinList(discordDefaultBot.path("allowList")));
        props.put("agent.channels.discord.token", discordDefaultBot == null ? "" : trim(discordDefaultBot.path("token").asString("")));
        props.put("agent.channels.discord.bot-user-id", discordDefaultBot == null ? "" : trim(discordDefaultBot.path("botUserId").asString("")));
        props.put("agent.channels.discord.accept-bot-messages", discordDefaultBot != null && discordDefaultBot.path("acceptBotMessages").asBoolean(false));
        props.put("agent.channels.telegram.enabled", telegramEnabled && telegramDefaultBot != null);
        props.put("agent.channels.telegram.require-mention", telegramDefaultBot != null && telegramDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.telegram.allow-list", telegramDefaultBot == null ? "" : joinList(telegramDefaultBot.path("allowList")));
        props.put("agent.channels.telegram.token", telegramDefaultBot == null ? "" : trim(telegramDefaultBot.path("token").asString("")));
        props.put("agent.channels.telegram.bot-username", telegramDefaultBot == null ? "" : normalizeUsername(telegramDefaultBot.path("botUsername").asString("")));
        props.put("agent.channels.qq.enabled", qqEnabled && qqDefaultBot != null);
        props.put("agent.channels.qq.require-mention", qqDefaultBot != null && qqDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.qq.allow-list", qqDefaultBot == null ? "" : joinList(qqDefaultBot.path("allowList")));
        props.put("agent.channels.qq.app-id", qqDefaultBot == null ? "" : trim(qqDefaultBot.path("appId").asString("")));
        props.put("agent.channels.qq.client-secret", qqDefaultBot == null ? "" : trim(qqDefaultBot.path("clientSecret").asString(qqDefaultBot.path("token").asString(""))));
        props.put("agent.channels.qq.bot-user-id", qqDefaultBot == null ? "" : trim(qqDefaultBot.path("botUserId").asString("")));
        props.put("agent.channels.qq.sandbox", qqDefaultBot != null && qqDefaultBot.path("sandbox").asBoolean(false));
        props.put("agent.channels.qq.markdown-enabled", qqDefaultBot != null && qqDefaultBot.path("markdownEnabled").asBoolean(false));
        props.put("agent.channels.wecom.enabled", wecomEnabled && wecomDefaultBot != null);
        props.put("agent.channels.wecom.require-mention", wecomDefaultBot != null && wecomDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.wecom.allow-list", wecomDefaultBot == null ? "" : joinList(wecomDefaultBot.path("allowList")));
        props.put("agent.channels.wecom.wecom-bot-id", wecomDefaultBot == null ? "" : trim(wecomDefaultBot.path("wecomBotId").asString(wecomDefaultBot.path("botId").asString(""))));
        props.put("agent.channels.wecom.secret", wecomDefaultBot == null ? "" : trim(wecomDefaultBot.path("secret").asString("")));
        props.put("agent.channels.weixin.enabled", weixinEnabled && weixinDefaultBot != null);
        props.put("agent.channels.weixin.require-mention", weixinDefaultBot != null && weixinDefaultBot.path("requireMention").asBoolean(true));
        props.put("agent.channels.weixin.allow-list", weixinDefaultBot == null ? "" : joinList(weixinDefaultBot.path("allowList")));
        props.put("agent.channels.weixin.bot-token", weixinDefaultBot == null ? "" : trim(weixinDefaultBot.path("botToken").asString("")));
        props.put("agent.channels.weixin.bot-token-file", weixinDefaultBot == null ? "" : trim(weixinDefaultBot.path("botTokenFile").asString("")));
        props.put("agent.channels.weixin.base-url", weixinDefaultBot == null ? "https://ilinkai.weixin.qq.com" : fallback(trim(weixinDefaultBot.path("baseUrl").asString("")), "https://ilinkai.weixin.qq.com"));
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
            String value = trim(item.asString(""));
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

    private String normalizeUsername(String value) {
        String username = trim(value);
        return username.startsWith("@") ? username.substring(1).trim() : username;
    }
}
