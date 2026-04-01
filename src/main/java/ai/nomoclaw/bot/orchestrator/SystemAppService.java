package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ChannelConfigDto;
import ai.nomoclaw.bot.application.dto.SystemConfigDto;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import ai.nomoclaw.bot.util.JsonUtil;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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
        JsonNode channelsNode = root.path("channels");
        ChannelConfigDto loaded = null;
        if (!channelsNode.isMissingNode() && !channelsNode.isNull()) {
            try {
                if (channelsNode instanceof ObjectNode channelsObject) {
                    if (channelsObject.path("feishu") instanceof ObjectNode feishu) {
                        feishu.remove("added");
                    }
                    if (channelsObject.path("dingtalk") instanceof ObjectNode dingtalk) {
                        dingtalk.remove("added");
                    }
                }
                ChannelConfigDto.Channels channels = MAPPER.treeToValue(channelsNode, ChannelConfigDto.Channels.class);
                loaded = new ChannelConfigDto(channels);
            } catch (Exception ignored) {
                loaded = null;
            }
        }
        ChannelConfigDto sanitized = sanitize(loaded == null ? ChannelConfigDto.defaults() : loaded);
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
            if (isBlank(feishu.appId()) || isBlank(feishu.appSecret())) {
                throw new IllegalArgumentException("feishu enabled requires appId and appSecret");
            }
        }
        if (dingtalk.enabled()) {
            if (isBlank(dingtalk.clientId()) || isBlank(dingtalk.clientSecret()) || isBlank(dingtalk.robotCode())) {
                throw new IllegalArgumentException("dingtalk enabled requires clientId, clientSecret and robotCode");
            }
        }
    }

    private ChannelConfigDto sanitize(ChannelConfigDto input) {
        ChannelConfigDto baseline = input == null ? ChannelConfigDto.defaults() : input;
        ChannelConfigDto.Channels channels = baseline.channels();
        if (channels == null) {
            channels = ChannelConfigDto.defaults().channels();
        }
        ChannelConfigDto.Feishu feishuInput = channels.feishu() == null ? ChannelConfigDto.defaults().channels().feishu() : channels.feishu();
        ChannelConfigDto.DingTalk dingInput = channels.dingtalk() == null ? ChannelConfigDto.defaults().channels().dingtalk() : channels.dingtalk();
        ChannelConfigDto.Feishu feishu = new ChannelConfigDto.Feishu(
                feishuInput.enabled(),
                feishuInput.requireMention(),
                normalizeList(feishuInput.allowList()),
                trim(feishuInput.appId()),
                trim(feishuInput.appSecret()),
                feishuInput.processingAckReactionEnabled(),
                fallback(trim(feishuInput.processingAckReactionType()), "OK")
        );
        ChannelConfigDto.DingTalk dingtalk = new ChannelConfigDto.DingTalk(
                dingInput.enabled(),
                dingInput.requireMention(),
                normalizeList(dingInput.allowList()),
                trim(dingInput.clientId()),
                trim(dingInput.clientSecret()),
                trim(dingInput.robotCode())
        );
        return new ChannelConfigDto(new ChannelConfigDto.Channels(feishu, dingtalk));
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
