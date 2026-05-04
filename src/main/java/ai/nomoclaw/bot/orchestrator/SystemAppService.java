package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ChannelConfigDto;
import ai.nomoclaw.bot.application.dto.SystemConfigDto;
import ai.nomoclaw.bot.channel.platform.FeishuBotTargetResolverService;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class SystemAppService {

    private static final String CONFIG_FILE_NAME = "nomoclaw.json";
    private static final ObjectMapper MAPPER = JsonUtil.mapper();

    private final AgentApplicationService facade;
    private final FeishuBotTargetResolverService feishuBotTargetResolverService;

    public SystemAppService(AgentApplicationService facade,
                            FeishuBotTargetResolverService feishuBotTargetResolverService) {
        this.facade = facade;
        this.feishuBotTargetResolverService = feishuBotTargetResolverService;
    }

    public SystemConfigDto getSystemConfig() {
        return facade.getSystemConfig();
    }

    public void openFile(String path) {
        facade.openFile(path);
    }

    public synchronized ChannelConfigDto getChannelConfig() {
        ObjectNode root = readRootConfig();
        ObjectNode channelsNode = normalizeChannelsNode(root);
        ChannelConfigDto loaded;
        try {
            ChannelConfigDto.Channels channels = MAPPER.treeToValue(channelsNode, ChannelConfigDto.Channels.class);
            loaded = new ChannelConfigDto(channels);
        } catch (Exception ex) {
            loaded = ChannelConfigDto.defaults();
        }
        ChannelConfigDto sanitized = sanitize(loaded);
        root.set("channels", MAPPER.valueToTree(sanitized.channels()));
        writeRootConfig(root);
        return sanitized;
    }

    public synchronized ChannelConfigDto updateChannelConfig(ChannelConfigDto request) {
        ChannelConfigDto sanitized = sanitize(request);
        sanitized = enrichFeishuDefaultTargets(sanitized);
        validateRequired(sanitized);
        ObjectNode root = readRootConfig();
        root.set("channels", MAPPER.valueToTree(sanitized.channels()));
        writeRootConfig(root);
        return sanitized;
    }

    private void validateRequired(ChannelConfigDto dto) {
        ChannelConfigDto.Feishu feishu = dto.channels().feishu();
        ChannelConfigDto.DingTalk dingtalk = dto.channels().dingtalk();
        ChannelConfigDto.Discord discord = dto.channels().discord();
        ChannelConfigDto.Telegram telegram = dto.channels().telegram();
        if (feishu.enabled()) {
            List<ChannelConfigDto.FeishuBot> enabledBots = feishu.bots().stream().filter(ChannelConfigDto.FeishuBot::enabled).toList();
            if (enabledBots.isEmpty()) {
                throw new IllegalArgumentException("feishu enabled requires at least one enabled bot");
            }
            for (ChannelConfigDto.FeishuBot bot : enabledBots) {
                if (isBlank(bot.appId()) || isBlank(bot.appSecret())) {
                    throw new IllegalArgumentException("feishu enabled bot requires appId and appSecret: " + bot.botId());
                }
            }
        }
        if (dingtalk.enabled()) {
            List<ChannelConfigDto.DingTalkBot> enabledBots = dingtalk.bots().stream().filter(ChannelConfigDto.DingTalkBot::enabled).toList();
            if (enabledBots.isEmpty()) {
                throw new IllegalArgumentException("dingtalk enabled requires at least one enabled bot");
            }
            for (ChannelConfigDto.DingTalkBot bot : enabledBots) {
                if (isBlank(bot.clientId()) || isBlank(bot.clientSecret()) || isBlank(bot.robotCode())) {
                    throw new IllegalArgumentException("dingtalk enabled bot requires clientId/clientSecret/robotCode: " + bot.botId());
                }
            }
        }
        if (discord.enabled()) {
            List<ChannelConfigDto.DiscordBot> enabledBots = discord.bots().stream().filter(ChannelConfigDto.DiscordBot::enabled).toList();
            if (enabledBots.isEmpty()) {
                throw new IllegalArgumentException("discord enabled requires at least one enabled bot");
            }
            for (ChannelConfigDto.DiscordBot bot : enabledBots) {
                if (isBlank(bot.token())) {
                    throw new IllegalArgumentException("discord enabled bot requires token: " + bot.botId());
                }
            }
        }
        if (telegram.enabled()) {
            List<ChannelConfigDto.TelegramBot> enabledBots = telegram.bots().stream().filter(ChannelConfigDto.TelegramBot::enabled).toList();
            if (enabledBots.isEmpty()) {
                throw new IllegalArgumentException("telegram enabled requires at least one enabled bot");
            }
            for (ChannelConfigDto.TelegramBot bot : enabledBots) {
                if (isBlank(bot.token())) {
                    throw new IllegalArgumentException("telegram enabled bot requires token: " + bot.botId());
                }
            }
        }
    }

    private ChannelConfigDto sanitize(ChannelConfigDto input) {
        ChannelConfigDto baseline = input == null ? ChannelConfigDto.defaults() : input;
        ChannelConfigDto.Channels channels = baseline.channels() == null ? ChannelConfigDto.defaults().channels() : baseline.channels();
        ChannelConfigDto.Feishu feishuInput = channels.feishu() == null ? ChannelConfigDto.defaults().channels().feishu() : channels.feishu();
        ChannelConfigDto.DingTalk dingInput = channels.dingtalk() == null ? ChannelConfigDto.defaults().channels().dingtalk() : channels.dingtalk();
        ChannelConfigDto.Discord discordInput = channels.discord() == null ? ChannelConfigDto.defaults().channels().discord() : channels.discord();
        ChannelConfigDto.Telegram telegramInput = channels.telegram() == null ? ChannelConfigDto.defaults().channels().telegram() : channels.telegram();

        List<ChannelConfigDto.FeishuBot> feishuBots = sanitizeFeishuBots(feishuInput.bots());
        List<ChannelConfigDto.DingTalkBot> dingBots = sanitizeDingTalkBots(dingInput.bots());
        List<ChannelConfigDto.DiscordBot> discordBots = sanitizeDiscordBots(discordInput.bots());
        List<ChannelConfigDto.TelegramBot> telegramBots = sanitizeTelegramBots(telegramInput.bots());
        return new ChannelConfigDto(new ChannelConfigDto.Channels(
                new ChannelConfigDto.Feishu(feishuInput.enabled(), feishuBots),
                new ChannelConfigDto.DingTalk(dingInput.enabled(), dingBots),
                new ChannelConfigDto.Discord(discordInput.enabled(), discordBots),
                new ChannelConfigDto.Telegram(telegramInput.enabled(), telegramBots)
        ));
    }

    private List<ChannelConfigDto.FeishuBot> sanitizeFeishuBots(List<ChannelConfigDto.FeishuBot> raw) {
        List<ChannelConfigDto.FeishuBot> bots = raw == null || raw.isEmpty() ? List.of(ChannelConfigDto.defaultFeishuBot()) : raw;
        Set<String> seen = new LinkedHashSet<>();
        List<ChannelConfigDto.FeishuBot> normalized = bots.stream()
                .filter(Objects::nonNull)
                .map(bot -> {
                    String botId = ensureUniqueBotId(normalizeBotId(bot.botId()), seen);
                    return new ChannelConfigDto.FeishuBot(
                            botId,
                            fallback(trim(bot.displayName()), "Feishu " + botId),
                            bot.enabled(),
                            bot.isDefault(),
                            bot.requireMention(),
                            normalizeList(bot.allowList()),
                            trim(bot.appId()),
                            trim(bot.appSecret()),
                            bot.processingAckReactionEnabled(),
                            fallback(trim(bot.processingAckReactionType()), "OK"),
                            trim(bot.defaultTarget()),
                            trim(bot.defaultTargetDisplayName()),
                            trim(bot.targetResolvedAt())
                    );
                })
                .toList();
        return enforceSingleDefault(normalized, ChannelConfigDto.defaultFeishuBot().botId());
    }

    private List<ChannelConfigDto.DingTalkBot> sanitizeDingTalkBots(List<ChannelConfigDto.DingTalkBot> raw) {
        List<ChannelConfigDto.DingTalkBot> bots = raw == null || raw.isEmpty() ? List.of(ChannelConfigDto.defaultDingTalkBot()) : raw;
        Set<String> seen = new LinkedHashSet<>();
        List<ChannelConfigDto.DingTalkBot> normalized = bots.stream()
                .filter(Objects::nonNull)
                .map(bot -> {
                    String botId = ensureUniqueBotId(normalizeBotId(bot.botId()), seen);
                    return new ChannelConfigDto.DingTalkBot(
                            botId,
                            fallback(trim(bot.displayName()), "DingTalk " + botId),
                            bot.enabled(),
                            bot.isDefault(),
                            bot.requireMention(),
                            normalizeList(bot.allowList()),
                            trim(bot.clientId()),
                            trim(bot.clientSecret()),
                            trim(bot.robotCode())
                    );
                })
                .toList();
        return enforceSingleDefaultDingTalk(normalized, ChannelConfigDto.defaultDingTalkBot().botId());
    }

    private List<ChannelConfigDto.DiscordBot> sanitizeDiscordBots(List<ChannelConfigDto.DiscordBot> raw) {
        List<ChannelConfigDto.DiscordBot> bots = raw == null || raw.isEmpty() ? List.of(ChannelConfigDto.defaultDiscordBot()) : raw;
        Set<String> seen = new LinkedHashSet<>();
        List<ChannelConfigDto.DiscordBot> normalized = bots.stream()
                .filter(Objects::nonNull)
                .map(bot -> {
                    String botId = ensureUniqueBotId(normalizeBotId(bot.botId()), seen);
                    return new ChannelConfigDto.DiscordBot(
                            botId,
                            fallback(trim(bot.displayName()), "Discord " + botId),
                            bot.enabled(),
                            bot.isDefault(),
                            bot.requireMention(),
                            normalizeList(bot.allowList()),
                            trim(bot.token()),
                            trim(bot.botUserId()),
                            bot.acceptBotMessages()
                    );
                })
                .toList();
        return enforceSingleDefaultDiscord(normalized, ChannelConfigDto.defaultDiscordBot().botId());
    }

    private List<ChannelConfigDto.TelegramBot> sanitizeTelegramBots(List<ChannelConfigDto.TelegramBot> raw) {
        List<ChannelConfigDto.TelegramBot> bots = raw == null || raw.isEmpty() ? List.of(ChannelConfigDto.defaultTelegramBot()) : raw;
        Set<String> seen = new LinkedHashSet<>();
        List<ChannelConfigDto.TelegramBot> normalized = bots.stream()
                .filter(Objects::nonNull)
                .map(bot -> {
                    String botId = ensureUniqueBotId(normalizeBotId(bot.botId()), seen);
                    return new ChannelConfigDto.TelegramBot(
                            botId,
                            fallback(trim(bot.displayName()), "Telegram " + botId),
                            bot.enabled(),
                            bot.isDefault(),
                            bot.requireMention(),
                            normalizeList(bot.allowList()),
                            trim(bot.token()),
                            normalizeTelegramUsername(bot.botUsername())
                    );
                })
                .toList();
        return enforceSingleDefaultTelegram(normalized, ChannelConfigDto.defaultTelegramBot().botId());
    }

    private List<ChannelConfigDto.FeishuBot> enforceSingleDefault(List<ChannelConfigDto.FeishuBot> bots, String fallbackBotId) {
        if (bots.isEmpty()) {
            return List.of(ChannelConfigDto.defaultFeishuBot());
        }
        String defaultBotId = resolveDefaultBotId(
                bots.stream().filter(ChannelConfigDto.FeishuBot::isDefault).map(ChannelConfigDto.FeishuBot::botId).toList(),
                bots.stream().map(ChannelConfigDto.FeishuBot::botId).toList(),
                fallbackBotId
        );
        return bots.stream()
                .map(bot -> new ChannelConfigDto.FeishuBot(
                        bot.botId(),
                        bot.displayName(),
                        bot.enabled(),
                        bot.botId().equals(defaultBotId),
                        bot.requireMention(),
                        bot.allowList(),
                        bot.appId(),
                        bot.appSecret(),
                        bot.processingAckReactionEnabled(),
                        bot.processingAckReactionType(),
                        bot.defaultTarget(),
                        bot.defaultTargetDisplayName(),
                        bot.targetResolvedAt()
                ))
                .toList();
    }

    private List<ChannelConfigDto.DingTalkBot> enforceSingleDefaultDingTalk(List<ChannelConfigDto.DingTalkBot> bots, String fallbackBotId) {
        if (bots.isEmpty()) {
            return List.of(ChannelConfigDto.defaultDingTalkBot());
        }
        String defaultBotId = resolveDefaultBotId(
                bots.stream().filter(ChannelConfigDto.DingTalkBot::isDefault).map(ChannelConfigDto.DingTalkBot::botId).toList(),
                bots.stream().map(ChannelConfigDto.DingTalkBot::botId).toList(),
                fallbackBotId
        );
        return bots.stream()
                .map(bot -> new ChannelConfigDto.DingTalkBot(
                        bot.botId(),
                        bot.displayName(),
                        bot.enabled(),
                        bot.botId().equals(defaultBotId),
                        bot.requireMention(),
                        bot.allowList(),
                        bot.clientId(),
                        bot.clientSecret(),
                        bot.robotCode()
                ))
                .toList();
    }

    private List<ChannelConfigDto.DiscordBot> enforceSingleDefaultDiscord(List<ChannelConfigDto.DiscordBot> bots, String fallbackBotId) {
        if (bots.isEmpty()) {
            return List.of(ChannelConfigDto.defaultDiscordBot());
        }
        String defaultBotId = resolveDefaultBotId(
                bots.stream().filter(ChannelConfigDto.DiscordBot::isDefault).map(ChannelConfigDto.DiscordBot::botId).toList(),
                bots.stream().map(ChannelConfigDto.DiscordBot::botId).toList(),
                fallbackBotId
        );
        return bots.stream()
                .map(bot -> new ChannelConfigDto.DiscordBot(
                        bot.botId(),
                        bot.displayName(),
                        bot.enabled(),
                        bot.botId().equals(defaultBotId),
                        bot.requireMention(),
                        bot.allowList(),
                        bot.token(),
                        bot.botUserId(),
                        bot.acceptBotMessages()
                ))
                .toList();
    }

    private List<ChannelConfigDto.TelegramBot> enforceSingleDefaultTelegram(List<ChannelConfigDto.TelegramBot> bots, String fallbackBotId) {
        if (bots.isEmpty()) {
            return List.of(ChannelConfigDto.defaultTelegramBot());
        }
        String defaultBotId = resolveDefaultBotId(
                bots.stream().filter(ChannelConfigDto.TelegramBot::isDefault).map(ChannelConfigDto.TelegramBot::botId).toList(),
                bots.stream().map(ChannelConfigDto.TelegramBot::botId).toList(),
                fallbackBotId
        );
        return bots.stream()
                .map(bot -> new ChannelConfigDto.TelegramBot(
                        bot.botId(),
                        bot.displayName(),
                        bot.enabled(),
                        bot.botId().equals(defaultBotId),
                        bot.requireMention(),
                        bot.allowList(),
                        bot.token(),
                        bot.botUsername()
                ))
                .toList();
    }

    private String resolveDefaultBotId(List<String> explicitDefaults, List<String> allBotIds, String fallbackBotId) {
        if (!explicitDefaults.isEmpty()) {
            return explicitDefaults.get(0);
        }
        if (!allBotIds.isEmpty()) {
            return allBotIds.get(0);
        }
        return fallbackBotId;
    }

    private String ensureUniqueBotId(String candidate, Set<String> seen) {
        String base = candidate.isBlank() ? "bot" : candidate;
        String current = base;
        int seq = 2;
        while (seen.contains(current)) {
            current = base + "_" + seq++;
        }
        seen.add(current);
        return current;
    }

    private String normalizeBotId(String raw) {
        String trimmed = trim(raw).toLowerCase(Locale.ROOT);
        String normalized = trimmed.replaceAll("[^a-z0-9_-]+", "_");
        if (normalized.isBlank()) {
            return "default";
        }
        return normalized;
    }

    private List<String> normalizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private ObjectNode normalizeChannelsNode(ObjectNode root) {
        ObjectNode channels = root.path("channels") instanceof ObjectNode node ? node : MAPPER.createObjectNode();
        root.set("channels", channels);
        channels.set("feishu", normalizeFeishuNode(channels.path("feishu")));
        channels.set("dingtalk", normalizeDingTalkNode(channels.path("dingtalk")));
        channels.set("discord", normalizeDiscordNode(channels.path("discord")));
        channels.set("telegram", normalizeTelegramNode(channels.path("telegram")));
        return channels;
    }

    private ObjectNode normalizeFeishuNode(JsonNode node) {
        ObjectNode feishu = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        feishu.remove("added");
        boolean enabled = feishu.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode rawBots = feishu.path("bots");
        if (rawBots.isArray()) {
            rawBots.forEach(bots::add);
        } else {
            ObjectNode migrated = MAPPER.createObjectNode();
            migrated.put("botId", "default");
            migrated.put("displayName", "Feishu Default");
            migrated.put("enabled", enabled);
            migrated.put("isDefault", true);
            migrated.put("requireMention", feishu.path("requireMention").asBoolean(true));
            migrated.set("allowList", feishu.path("allowList").isArray() ? feishu.path("allowList") : MAPPER.createArrayNode());
            migrated.put("appId", trim(feishu.path("appId").asText("")));
            migrated.put("appSecret", trim(feishu.path("appSecret").asText("")));
            migrated.put("processingAckReactionEnabled", feishu.path("processingAckReactionEnabled").asBoolean(true));
            migrated.put("processingAckReactionType", fallback(trim(feishu.path("processingAckReactionType").asText("")), "OK"));
            migrated.put("defaultTarget", "");
            migrated.put("defaultTargetDisplayName", "");
            migrated.put("targetResolvedAt", "");
            bots.add(migrated);
        }
        feishu.removeAll();
        feishu.put("enabled", enabled);
        feishu.set("bots", bots);
        return feishu;
    }

    private ChannelConfigDto enrichFeishuDefaultTargets(ChannelConfigDto config) {
        List<ChannelConfigDto.FeishuBot> updatedBots = config.channels().feishu().bots().stream()
                .map(this::enrichFeishuBotTarget)
                .toList();
        return new ChannelConfigDto(new ChannelConfigDto.Channels(
                new ChannelConfigDto.Feishu(config.channels().feishu().enabled(), updatedBots),
                config.channels().dingtalk(),
                config.channels().discord(),
                config.channels().telegram()
        ));
    }

    private ChannelConfigDto.FeishuBot enrichFeishuBotTarget(ChannelConfigDto.FeishuBot bot) {
        String appId = trim(bot.appId());
        String appSecret = trim(bot.appSecret());
        if (appId.isBlank() || appSecret.isBlank()) {
            return new ChannelConfigDto.FeishuBot(
                    bot.botId(),
                    bot.displayName(),
                    bot.enabled(),
                    bot.isDefault(),
                    bot.requireMention(),
                    bot.allowList(),
                    bot.appId(),
                    bot.appSecret(),
                    bot.processingAckReactionEnabled(),
                    bot.processingAckReactionType(),
                    "",
                    "",
                    ""
            );
        }
        FeishuBotTargetResolverService.ResolveResult result = feishuBotTargetResolverService.resolve(appId, appSecret);
        if (!result.resolved()) {
            log.warn("[ChannelConfig] feishu bot target unresolved botId={} reason={}", bot.botId(), result.error());
            return new ChannelConfigDto.FeishuBot(
                    bot.botId(),
                    bot.displayName(),
                    bot.enabled(),
                    bot.isDefault(),
                    bot.requireMention(),
                    bot.allowList(),
                    bot.appId(),
                    bot.appSecret(),
                    bot.processingAckReactionEnabled(),
                    bot.processingAckReactionType(),
                    trim(bot.defaultTarget()),
                    trim(bot.defaultTargetDisplayName()),
                    trim(bot.targetResolvedAt())
            );
        }
        log.info("[ChannelConfig] feishu bot target resolved botId={} target={}",
                bot.botId(), maskTarget(result.defaultTarget()));
        return new ChannelConfigDto.FeishuBot(
                bot.botId(),
                bot.displayName(),
                bot.enabled(),
                bot.isDefault(),
                bot.requireMention(),
                bot.allowList(),
                bot.appId(),
                bot.appSecret(),
                bot.processingAckReactionEnabled(),
                bot.processingAckReactionType(),
                result.defaultTarget(),
                result.defaultTargetDisplayName(),
                result.resolvedAt()
        );
    }

    private String maskTarget(String value) {
        String raw = trim(value);
        if (raw.isBlank()) {
            return "";
        }
        int index = raw.lastIndexOf(':');
        if (index < 0 || index == raw.length() - 1) {
            return "***";
        }
        String prefix = raw.substring(0, index + 1);
        String id = raw.substring(index + 1);
        if (id.length() <= 8) {
            return prefix + "***";
        }
        return prefix + id.substring(0, 4) + "..." + id.substring(id.length() - 4);
    }

    private ObjectNode normalizeDingTalkNode(JsonNode node) {
        ObjectNode dingtalk = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        dingtalk.remove("added");
        boolean enabled = dingtalk.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode rawBots = dingtalk.path("bots");
        if (rawBots.isArray()) {
            rawBots.forEach(bots::add);
        } else {
            ObjectNode migrated = MAPPER.createObjectNode();
            migrated.put("botId", "default");
            migrated.put("displayName", "DingTalk Default");
            migrated.put("enabled", enabled);
            migrated.put("isDefault", true);
            migrated.put("requireMention", dingtalk.path("requireMention").asBoolean(true));
            migrated.set("allowList", dingtalk.path("allowList").isArray() ? dingtalk.path("allowList") : MAPPER.createArrayNode());
            migrated.put("clientId", trim(dingtalk.path("clientId").asText("")));
            migrated.put("clientSecret", trim(dingtalk.path("clientSecret").asText("")));
            migrated.put("robotCode", trim(dingtalk.path("robotCode").asText("")));
            bots.add(migrated);
        }
        dingtalk.removeAll();
        dingtalk.put("enabled", enabled);
        dingtalk.set("bots", bots);
        return dingtalk;
    }

    private ObjectNode normalizeDiscordNode(JsonNode node) {
        ObjectNode discord = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        discord.remove("added");
        boolean enabled = discord.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode rawBots = discord.path("bots");
        if (rawBots.isArray()) {
            rawBots.forEach(bots::add);
        } else {
            ObjectNode migrated = MAPPER.createObjectNode();
            migrated.put("botId", "default");
            migrated.put("displayName", "Discord Default");
            migrated.put("enabled", enabled);
            migrated.put("isDefault", true);
            migrated.put("requireMention", discord.path("requireMention").asBoolean(true));
            migrated.set("allowList", discord.path("allowList").isArray() ? discord.path("allowList") : MAPPER.createArrayNode());
            migrated.put("token", trim(discord.path("token").asText("")));
            migrated.put("botUserId", trim(discord.path("botUserId").asText("")));
            migrated.put("acceptBotMessages", discord.path("acceptBotMessages").asBoolean(false));
            bots.add(migrated);
        }
        discord.removeAll();
        discord.put("enabled", enabled);
        discord.set("bots", bots);
        return discord;
    }

    private ObjectNode normalizeTelegramNode(JsonNode node) {
        ObjectNode telegram = node instanceof ObjectNode object ? object.deepCopy() : MAPPER.createObjectNode();
        telegram.remove("added");
        boolean enabled = telegram.path("enabled").asBoolean(false);
        ArrayNode bots = MAPPER.createArrayNode();
        JsonNode rawBots = telegram.path("bots");
        if (rawBots.isArray()) {
            rawBots.forEach(bots::add);
        } else {
            ObjectNode migrated = MAPPER.createObjectNode();
            migrated.put("botId", "default");
            migrated.put("displayName", "Telegram Default");
            migrated.put("enabled", enabled);
            migrated.put("isDefault", true);
            migrated.put("requireMention", telegram.path("requireMention").asBoolean(true));
            migrated.set("allowList", telegram.path("allowList").isArray() ? telegram.path("allowList") : MAPPER.createArrayNode());
            migrated.put("token", trim(telegram.path("token").asText("")));
            migrated.put("botUsername", normalizeTelegramUsername(telegram.path("botUsername").asText("")));
            bots.add(migrated);
        }
        telegram.removeAll();
        telegram.put("enabled", enabled);
        telegram.set("bots", bots);
        return telegram;
    }

    private ObjectNode readRootConfig() {
        Path file = configFilePath();
        if (Files.notExists(file)) {
            return MAPPER.createObjectNode();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return MAPPER.createObjectNode();
            }
            JsonNode node = MAPPER.readTree(content);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
            return MAPPER.createObjectNode();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read nomoclaw config: " + file, ex);
        }
    }

    private void writeRootConfig(ObjectNode root) {
        Path file = configFilePath();
        Path dir = file.getParent();
        try {
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            String pretty = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(tmp, pretty, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to write nomoclaw config: " + file, ex);
        }
    }

    private Path configFilePath() {
        return NomoClawPaths.root().resolve(CONFIG_FILE_NAME).toAbsolutePath().normalize();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeTelegramUsername(String value) {
        String username = trim(value);
        return username.startsWith("@") ? username.substring(1).trim() : username;
    }
}
