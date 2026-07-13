package ai.nomoclaw.bot.util;

import java.util.UUID;

public final class UuidUtil {

    private UuidUtil() {
    }

    public static String newUuid() {
        return normalize(UUID.randomUUID().toString());
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.replace("-", "");
    }
}
