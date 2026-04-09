package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ChannelConfigDto;
import ai.nomoclaw.bot.application.dto.SystemConfigDto;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class SystemAppService {

    private static final String CONFIG_FILE_NAME = "nomoclaw.json";
    private static final ObjectMapper MAPPER = JsonUtil.mapper();

    private final AgentApplicationService facade;

    public SystemAppService(AgentApplicationService facade) {
        this.facade = facade;
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
        validateRequired(sanitized);
        ObjectNode root = readRootConfig();
        root.set("channels", MAPPER.valueToTree(sanitized.channels()));
        writeRootConfig(root);
        return sanitized;
    }

    private void validateRequired(ChannelConfigDto dto) {
        ChannelConfigDto.Feishu feishu = dto.channels().feishu();
        ChannelConfigDto.DingTalk dingtalk = dto.channels().dingtalk();
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
    }

    private ChannelConfigDto sanitize(ChannelConfigDto input) {
        ChannelConfigDto baseline = input == null ? ChannelConfigDto.defaults() : input;
        ChannelConfigDto.Channels channels = baseline.channels() == null ? ChannelConfigDto.defaults().channels() : baseline.channels();
        ChannelConfigDto.Feishu feishuInput = channels.feishu() == null ? ChannelConfigDto.defaults().channels().feishu() : channels.feishu();
        ChannelConfigDto.DingTalk dingInput = channels.dingtalk() == null ? ChannelConfigDto.defaults().channels().dingtalk() : channels.dingtalk();

        List<ChannelConfigDto.FeishuBot> feishuBots = sanitizeFeishuBots(feishuInput.bots());
        List<ChannelConfigDto.DingTalkBot> dingBots = sanitizeDingTalkBots(dingInput.bots());
        return new ChannelConfigDto(new ChannelConfigDto.Channels(
                new ChannelConfigDto.Feishu(feishuInput.enabled(), feishuBots),
                new ChannelConfigDto.DingTalk(dingInput.enabled(), dingBots)
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
                            fallback(trim(bot.processingAckReactionType()), "OK")
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
                        bot.processingAckReactionType()
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
            bots.add(migrated);
        }
        feishu.removeAll();
        feishu.put("enabled", enabled);
        feishu.set("bots", bots);
        return feishu;
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
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
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
}
