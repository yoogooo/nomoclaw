package ai.nomoclaw.bot.policy.tool.config;

import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Component
public class ToolPolicyConfigProvider {

    private static final List<String> CONFIG_FILE_NAMES = List.of("settings.json", "nomoclaw.json");
    private static final ObjectMapper MAPPER = JsonUtil.mapper();

    public ToolPolicyConfig current() {
        ToolPolicyConfig defaults = ToolPolicyConfig.defaults();
        try {
            for (String fileName : CONFIG_FILE_NAMES) {
                Path file = NomoClawPaths.root().resolve(fileName).toAbsolutePath().normalize();
                if (Files.notExists(file)) {
                    continue;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content == null || content.isBlank()) {
                    continue;
                }
                JsonNode root = MAPPER.readTree(content);
                JsonNode policies = root.path("toolPolicies");
                if (!policies.isObject()) {
                    continue;
                }
                return new ToolPolicyConfig(
                        normalizeList(policies.path("allowWritePaths"), defaults.allowWritePaths()),
                        normalizeList(policies.path("denyWritePaths"), defaults.denyWritePaths()),
                        normalizeList(policies.path("highRiskCommandPatterns"), defaults.highRiskCommandPatterns()),
                        normalizeList(policies.path("highRiskFileActions"), defaults.highRiskFileActions())
                );
            }
            return defaults;
        } catch (Exception ignored) {
            return defaults;
        }
    }

    private List<String> normalizeList(JsonNode node, List<String> fallback) {
        if (!node.isArray()) {
            return fallback;
        }
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (JsonNode item : node) {
            String text = item == null ? "" : item.asString("");
            if (Objects.isNull(text)) {
                continue;
            }
            String normalized = text.trim();
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return values.isEmpty() ? fallback : java.util.List.copyOf(values);
    }
}
