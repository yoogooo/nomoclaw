package ai.nomoclaw.bot.mcp;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class McpToolKeyGenerator {

    public String generate(String serverName, String originalToolName) {
        String serverSlug = slug(serverName, 18);
        String toolSlug = slug(originalToolName, 24);
        String hash = hash(serverName + ":" + originalToolName).substring(0, 10);
        String key = "mcp_" + serverSlug + "_" + hash + "_" + toolSlug;
        return key.length() <= 64 ? key : key.substring(0, 64);
    }

    private String slug(String raw, int maxLength) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        String slug = normalized.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            slug = "tool";
        }
        return slug.length() <= maxLength ? slug : slug.substring(0, maxLength);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to generate mcp tool hash", ex);
        }
    }
}
