package ai.nomoclaw.bot.policy.tool.permission;

import ai.nomoclaw.bot.tool.PathResolver;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class PermissionSettingsStore {

    private static final ObjectMapper MAPPER = JsonUtil.mapper();
    private static final int SCHEMA_VERSION = 2;
    private static final String USER_PERMISSION_SETTINGS_FILE = "permission-settings.json";
    private static final String LEGACY_USER_SETTINGS_FILE = "settings.json";

    public List<PermissionRule> loadUserRules() {
        Path selected = preferredUserSettingsReadPath();
        ObjectNode root = readJson(selected);
        if (selected.equals(legacyUserSettingsPath()) && Files.exists(selected)) {
            log.info("[Permission] loaded legacy user settings file path={}, please migrate to {}", selected, userSettingsPath());
        }
        List<PermissionRule> rules = parseRules(root.path("permission").path("userSettings").path("rules"), PermissionSource.USER_SETTINGS);
        if (!rules.isEmpty()) {
            return rules;
        }
        return fromLegacyToolPolicies(root, PermissionSource.USER_SETTINGS);
    }

    public List<PermissionRule> loadAgentRules(String agentName) {
        ObjectNode root = readJson(agentSettingsPath(agentName));
        return parseRules(root.path("permission").path("agentSettings").path("rules"), PermissionSource.AGENT_SETTINGS);
    }

    public synchronized void saveUserRules(List<PermissionRule> rules) {
        Path path = userSettingsPath();
        ObjectNode root = readJson(preferredUserSettingsReadPath());
        ObjectNode permission = ensureObject(root, "permission");
        permission.put("schemaVersion", SCHEMA_VERSION);
        ObjectNode userSettings = ensureObject(permission, "userSettings");
        userSettings.set("rules", toArray(filterBySource(rules, PermissionSource.USER_SETTINGS)));
        writeJson(path, root);
    }

    public synchronized void saveAgentRules(String agentName, List<PermissionRule> rules) {
        Path path = agentSettingsPath(agentName);
        ObjectNode root = readJson(path);
        ObjectNode permission = ensureObject(root, "permission");
        permission.put("schemaVersion", SCHEMA_VERSION);
        ObjectNode agentSettings = ensureObject(permission, "agentSettings");
        agentSettings.set("rules", toArray(filterBySource(rules, PermissionSource.AGENT_SETTINGS)));
        writeJson(path, root);
    }

    public Path userSettingsPath() {
        return NomoClawPaths.root().resolve(USER_PERMISSION_SETTINGS_FILE).toAbsolutePath().normalize();
    }

    public Path legacyUserSettingsPath() {
        return NomoClawPaths.root().resolve(LEGACY_USER_SETTINGS_FILE).toAbsolutePath().normalize();
    }

    public Path agentSettingsPath(String agentName) {
        String normalized = (agentName == null || agentName.isBlank()) ? NomoClawPaths.DEFAULT_AGENT_NAME : agentName.trim();
        return NomoClawPaths.agentWorkspace(normalized).resolve("settings.local.json").toAbsolutePath().normalize();
    }

    private List<PermissionRule> filterBySource(List<PermissionRule> rules, PermissionSource source) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream().filter(rule -> rule != null && rule.source() == source).toList();
    }

    private List<PermissionRule> fromLegacyToolPolicies(ObjectNode root, PermissionSource source) {
        JsonNode policies = root.path("toolPolicies");
        if (!policies.isObject()) {
            return List.of();
        }
        List<PermissionRule> rules = new ArrayList<>();
        for (String path : stringList(policies.path("allowWritePaths"))) {
            rules.add(new PermissionRule(
                    "legacy-allow-" + UUID.randomUUID(),
                    source,
                    PermissionEffect.ALLOW,
                    "file_*",
                    "*",
                    PermissionResourceType.FILE,
                    path,
                    "",
                    null,
                    true
            ));
        }
        for (String path : stringList(policies.path("denyWritePaths"))) {
            rules.add(new PermissionRule(
                    "legacy-deny-" + UUID.randomUUID(),
                    source,
                    PermissionEffect.DENY,
                    "file_*",
                    "*",
                    PermissionResourceType.FILE,
                    path,
                    "",
                    null,
                    true
            ));
        }
        for (String pattern : stringList(policies.path("highRiskCommandPatterns"))) {
            rules.add(new PermissionRule(
                    "legacy-cmd-" + UUID.randomUUID(),
                    source,
                    PermissionEffect.ASK,
                    "command_tool",
                    "*",
                    PermissionResourceType.COMMAND,
                    "",
                    pattern,
                    null,
                    true
            ));
        }
        for (String action : stringList(policies.path("highRiskFileActions"))) {
            rules.add(new PermissionRule(
                    "legacy-file-action-" + UUID.randomUUID(),
                    source,
                    PermissionEffect.ASK,
                    "file_*",
                    action,
                    PermissionResourceType.FILE,
                    "",
                    "",
                    null,
                    true
            ));
        }
        if (!rules.isEmpty()) {
            log.info("[Permission] loaded {} legacy toolPolicies rules as {}", rules.size(), source);
        }
        return rules;
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String text = item == null ? "" : item.asText("").trim();
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private List<PermissionRule> parseRules(JsonNode node, PermissionSource source) {
        if (!node.isArray()) {
            return List.of();
        }
        List<PermissionRule> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            PermissionEffect effect = parseEffect(item.path("effect").asText(""));
            if (effect == null) {
                continue;
            }
            String ruleId = item.path("ruleId").asText("").trim();
            if (ruleId.isBlank()) {
                ruleId = source.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID();
            }
            Instant expiresAt = null;
            String expiresAtRaw = item.path("expiresAt").asText("").trim();
            if (!expiresAtRaw.isBlank()) {
                try {
                    expiresAt = Instant.parse(expiresAtRaw);
                } catch (Exception ignored) {
                    // ignore bad expiresAt
                }
            }
            out.add(new PermissionRule(
                    ruleId,
                    source,
                    effect,
                    normalizeTool(item.path("tool").asText("*")),
                    normalizeStar(item.path("action").asText("*")),
                    PermissionResourceType.from(item.path("resourceType").asText("*")),
                    normalizeOptional(item.path("pathPattern").asText("")),
                    normalizeOptional(item.path("commandPattern").asText("")),
                    expiresAt,
                    item.path("enabled").asBoolean(true)
            ));
        }
        return out;
    }

    private String normalizeTool(String tool) {
        String value = normalizeStar(tool);
        if ("file_*".equals(value)) {
            return "file_*";
        }
        return value;
    }

    private String normalizeStar(String value) {
        String normalized = value == null ? "*" : value.trim();
        return normalized.isBlank() ? "*" : normalized;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private PermissionEffect parseEffect(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PermissionEffect.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private ArrayNode toArray(List<PermissionRule> rules) {
        ArrayNode array = MAPPER.createArrayNode();
        if (rules == null) {
            return array;
        }
        for (PermissionRule rule : rules) {
            if (rule == null) {
                continue;
            }
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ruleId", rule.ruleId());
            node.put("effect", rule.effect().name());
            node.put("tool", normalizeTool(rule.tool()));
            node.put("action", normalizeStar(rule.action()));
            node.put("resourceType", rule.resourceType().name());
            node.put("pathPattern", rule.pathPattern() == null ? "" : rule.pathPattern());
            node.put("commandPattern", rule.commandPattern() == null ? "" : rule.commandPattern());
            if (rule.expiresAt() != null) {
                node.put("expiresAt", rule.expiresAt().toString());
            }
            node.put("enabled", rule.enabled());
            array.add(node);
        }
        return array;
    }

    private ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode existing = parent.path(field);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = MAPPER.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private ObjectNode readJson(Path path) {
        try {
            if (Files.notExists(path)) {
                return MAPPER.createObjectNode();
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                return MAPPER.createObjectNode();
            }
            JsonNode root = MAPPER.readTree(content);
            if (root instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (Exception ex) {
            log.warn("[Permission] failed to parse settings file, fallback empty path={} err={}", path, ex.getMessage());
        }
        return MAPPER.createObjectNode();
    }

    private void writeJson(Path path, ObjectNode root) {
        try {
            Files.createDirectories(path.getParent());
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write permission settings: " + path, ex);
        }
    }

    private Path preferredUserSettingsReadPath() {
        Path current = userSettingsPath();
        if (Files.exists(current)) {
            return current;
        }
        Path legacy = legacyUserSettingsPath();
        if (Files.exists(legacy)) {
            return legacy;
        }
        return current;
    }
}
