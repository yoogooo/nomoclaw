package ai.nomoclaw.bot.policy.tool.permission;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class BrowserPermissionSupport {
    private BrowserPermissionSupport() {
    }

    public static String extractHost(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean matchesDomain(String host, List<String> patterns) {
        if (host == null || host.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String normalizedPattern = pattern.trim().toLowerCase(Locale.ROOT);
            if (normalizedPattern.startsWith("*.")) {
                String suffix = normalizedPattern.substring(1);
                if (normalizedHost.endsWith(suffix)) {
                    return true;
                }
                continue;
            }
            if (normalizedHost.equals(normalizedPattern) || normalizedHost.endsWith("." + normalizedPattern)) {
                return true;
            }
        }
        return false;
    }

    public static Path browserDomainPath(String host) {
        String normalizedHost = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalizedHost.isBlank()) {
            normalizedHost = "unknown";
        }
        return Path.of("/browser-domain", normalizedHost).toAbsolutePath().normalize();
    }
}
