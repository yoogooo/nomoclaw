package ai.nomoclaw.bot.channel.config;

import ai.nomoclaw.bot.channel.model.ChannelType;
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
public class ChannelBotCredentialResolver {

    private static final ObjectMapper MAPPER = JsonUtil.mapper();
    private static final String CONFIG_FILE_NAME = "nomoclaw.json";

    public FeishuBotCredential resolveFeishu(String botId) {
        List<FeishuBotCredential> bots = listFeishuBots();
        if (bots.isEmpty()) {
            return null;
        }
        String normalizedBotId = normalizeLookupBotId(botId);
        if (normalizedBotId.isBlank()) {
            return findDefaultFeishuBot(bots);
        }
        for (FeishuBotCredential bot : bots) {
            if (bot.botId().equals(normalizedBotId) && bot.enabled()) {
                return bot;
            }
        }
        return null;
    }

    public DingTalkBotCredential resolveDingTalk(String botId) {
        List<DingTalkBotCredential> bots = listDingTalkBots();
        if (bots.isEmpty()) {
            return null;
        }
        String normalizedBotId = normalizeLookupBotId(botId);
        if (normalizedBotId.isBlank()) {
            return findDefaultDingTalkBot(bots);
        }
        for (DingTalkBotCredential bot : bots) {
            if (bot.botId().equals(normalizedBotId) && bot.enabled()) {
                return bot;
            }
        }
        return null;
    }

    public boolean hasEnabledBot(ChannelType channelType, String botId) {
        if (channelType == ChannelType.FEISHU) {
            return resolveFeishu(botId) != null;
        }
        if (channelType == ChannelType.DINGTALK) {
            return resolveDingTalk(botId) != null;
        }
        return false;
    }

    public String resolveDefaultBotId(ChannelType channelType) {
        if (channelType == ChannelType.FEISHU) {
            FeishuBotCredential bot = resolveFeishu("");
            return bot == null ? "" : bot.botId();
        }
        if (channelType == ChannelType.DINGTALK) {
            DingTalkBotCredential bot = resolveDingTalk("");
            return bot == null ? "" : bot.botId();
        }
        return "";
    }

    public String resolveDefaultTarget(ChannelType channelType, String botId) {
        if (channelType == ChannelType.FEISHU) {
            FeishuBotCredential bot = resolveFeishu(botId);
            return bot == null ? "" : trim(bot.defaultTarget());
        }
        return "";
    }

    public List<FeishuBotCredential> listFeishuBots() {
        JsonNode feishu = readChannelsNode().path("feishu");
        boolean channelEnabled = feishu.path("enabled").asBoolean(false);
        ArrayList<FeishuBotCredential> bots = new ArrayList<>();
        JsonNode botNodes = feishu.path("bots");
        if (botNodes.isArray()) {
            for (JsonNode bot : botNodes) {
                String botId = normalizeBotId(bot.path("botId").asText(""));
                bots.add(new FeishuBotCredential(
                        botId,
                        trim(bot.path("displayName").asText("")),
                        channelEnabled && bot.path("enabled").asBoolean(false),
                        bot.path("isDefault").asBoolean(false),
                        bot.path("requireMention").asBoolean(true),
                        trim(bot.path("appId").asText("")),
                        trim(bot.path("appSecret").asText("")),
                        bot.path("processingAckReactionEnabled").asBoolean(true),
                        fallback(trim(bot.path("processingAckReactionType").asText("")), "OK"),
                        trim(bot.path("defaultTarget").asText("")),
                        trim(bot.path("defaultTargetDisplayName").asText("")),
                        trim(bot.path("targetResolvedAt").asText(""))
                ));
            }
        } else {
            // Backward-compatible read from old single-bot shape.
            bots.add(new FeishuBotCredential(
                    "default",
                    "Feishu Default",
                    channelEnabled,
                    true,
                    feishu.path("requireMention").asBoolean(true),
                    trim(feishu.path("appId").asText("")),
                    trim(feishu.path("appSecret").asText("")),
                    feishu.path("processingAckReactionEnabled").asBoolean(true),
                    fallback(trim(feishu.path("processingAckReactionType").asText("")), "OK"),
                    "",
                    "",
                    ""
            ));
        }
        return List.copyOf(bots);
    }

    public List<DingTalkBotCredential> listDingTalkBots() {
        JsonNode dingtalk = readChannelsNode().path("dingtalk");
        boolean channelEnabled = dingtalk.path("enabled").asBoolean(false);
        ArrayList<DingTalkBotCredential> bots = new ArrayList<>();
        JsonNode botNodes = dingtalk.path("bots");
        if (botNodes.isArray()) {
            for (JsonNode bot : botNodes) {
                String botId = normalizeBotId(bot.path("botId").asText(""));
                bots.add(new DingTalkBotCredential(
                        botId,
                        trim(bot.path("displayName").asText("")),
                        channelEnabled && bot.path("enabled").asBoolean(false),
                        bot.path("isDefault").asBoolean(false),
                        bot.path("requireMention").asBoolean(true),
                        trim(bot.path("clientId").asText("")),
                        trim(bot.path("clientSecret").asText("")),
                        trim(bot.path("robotCode").asText(""))
                ));
            }
        } else {
            // Backward-compatible read from old single-bot shape.
            bots.add(new DingTalkBotCredential(
                    "default",
                    "DingTalk Default",
                    channelEnabled,
                    true,
                    dingtalk.path("requireMention").asBoolean(true),
                    trim(dingtalk.path("clientId").asText("")),
                    trim(dingtalk.path("clientSecret").asText("")),
                    trim(dingtalk.path("robotCode").asText(""))
            ));
        }
        return List.copyOf(bots);
    }

    private FeishuBotCredential findDefaultFeishuBot(List<FeishuBotCredential> bots) {
        for (FeishuBotCredential bot : bots) {
            if (bot.isDefault() && bot.enabled()) {
                return bot;
            }
        }
        for (FeishuBotCredential bot : bots) {
            if (bot.enabled()) {
                return bot;
            }
        }
        return null;
    }

    private DingTalkBotCredential findDefaultDingTalkBot(List<DingTalkBotCredential> bots) {
        for (DingTalkBotCredential bot : bots) {
            if (bot.isDefault() && bot.enabled()) {
                return bot;
            }
        }
        for (DingTalkBotCredential bot : bots) {
            if (bot.enabled()) {
                return bot;
            }
        }
        return null;
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

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public record FeishuBotCredential(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            String appId,
            String appSecret,
            boolean processingAckReactionEnabled,
            String processingAckReactionType,
            String defaultTarget,
            String defaultTargetDisplayName,
            String targetResolvedAt
    ) {
    }

    public record DingTalkBotCredential(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            String clientId,
            String clientSecret,
            String robotCode
    ) {
    }
}
