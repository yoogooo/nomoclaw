package ai.nomoclaw.bot.channel.config;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.system.model.ChannelConfigDto;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ChannelBotRouteResolver {

    private static final ObjectMapper MAPPER = JsonUtil.mapper();
    private static final String CONFIG_FILE_NAME = "nomoclaw.json";

    public BotRouteConfig resolve(ChannelType channelType, String botId) {
        List<BotRouteConfig> bots = list(channelType);
        if (bots.isEmpty()) {
            return defaultConfig("");
        }
        String normalizedBotId = normalizeLookupBotId(botId);
        if (normalizedBotId.isBlank()) {
            BotRouteConfig defaultBot = findDefaultBot(bots);
            return defaultBot == null ? defaultConfig("") : defaultBot;
        }
        for (BotRouteConfig bot : bots) {
            if (bot.enabled() && bot.botId().equals(normalizedBotId)) {
                return bot;
            }
        }
        BotRouteConfig defaultBot = findDefaultBot(bots);
        return defaultBot == null ? defaultConfig(normalizedBotId) : defaultBot;
    }

    public String resolveDefaultBotId(ChannelType channelType) {
        BotRouteConfig bot = findDefaultBot(list(channelType));
        return bot == null ? "" : bot.botId();
    }

    private List<BotRouteConfig> list(ChannelType channelType) {
        String key = switch (channelType) {
            case WEB, NOOP -> "";
            case FEISHU -> "feishu";
            case DINGTALK -> "dingtalk";
            case DISCORD -> "discord";
            case TELEGRAM -> "telegram";
            case QQ -> "qq";
            case WECOM -> "wecom";
            case WEIXIN -> "weixin";
        };
        if (key.isBlank()) {
            return List.of();
        }
        JsonNode channelNode = readChannelsNode().path(key);
        boolean channelEnabled = channelNode.path("enabled").asBoolean(false);
        ArrayList<BotRouteConfig> bots = new ArrayList<>();
        JsonNode botNodes = channelNode.path("bots");
        if (botNodes.isArray()) {
            for (JsonNode bot : botNodes) {
                bots.add(new BotRouteConfig(
                        normalizeBotId(bot.path("botId").asString("")),
                        channelEnabled && bot.path("enabled").asBoolean(false),
                        bot.path("isDefault").asBoolean(false),
                        normalizeAgentUid(bot.path("agentUid").asString("")),
                        trim(bot.path("defaultModelProvider").asString("")),
                        trim(bot.path("defaultModelName").asString(""))
                ));
            }
            return List.copyOf(bots);
        }
        bots.add(defaultConfig("default"));
        return List.copyOf(bots);
    }

    private BotRouteConfig findDefaultBot(List<BotRouteConfig> bots) {
        for (BotRouteConfig bot : bots) {
            if (bot.enabled() && bot.isDefault()) {
                return bot;
            }
        }
        for (BotRouteConfig bot : bots) {
            if (bot.enabled()) {
                return bot;
            }
        }
        return null;
    }

    private BotRouteConfig defaultConfig(String botId) {
        return new BotRouteConfig(
                normalizeBotId(botId),
                false,
                true,
                ChannelConfigDto.DEFAULT_AGENT_UID,
                "",
                ""
        );
    }

    private JsonNode readChannelsNode() {
        Path file = NomoClawPaths.root().resolve(CONFIG_FILE_NAME).toAbsolutePath().normalize();
        if (Files.notExists(file)) {
            return MAPPER.createObjectNode();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return MAPPER.createObjectNode();
            }
            JsonNode root = MAPPER.readTree(content);
            return root.path("channels");
        } catch (Exception ignored) {
            return MAPPER.createObjectNode();
        }
    }

    private String normalizeAgentUid(String agentUid) {
        String normalized = trim(agentUid);
        return normalized.isBlank() ? ChannelConfigDto.DEFAULT_AGENT_UID : normalized;
    }

    private String normalizeBotId(String value) {
        String raw = trim(value);
        if (raw.isBlank()) {
            return "default";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
    }

    private String normalizeLookupBotId(String value) {
        String raw = trim(value);
        if (raw.isBlank()) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public record BotRouteConfig(
            String botId,
            boolean enabled,
            boolean isDefault,
            String agentUid,
            String defaultModelProvider,
            String defaultModelName
    ) {
    }
}
