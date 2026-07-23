package ai.nomoclaw.bot.knowledge.util;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * UUID helpers for knowledge business identifiers.
 */
public final class UuidUtil {

    private UuidUtil() {
    }

    public static String newUuid() {
        return normalize(UUID.randomUUID().toString());
    }

    public static String stableUuid(String value) {
        return normalize(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString());
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.replace("-", "");
    }
}
