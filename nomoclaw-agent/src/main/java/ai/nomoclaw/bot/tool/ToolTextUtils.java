package ai.nomoclaw.bot.tool;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ToolTextUtils {

    private static final int DEFAULT_MAX_LINES = 1000;
    private static final int DEFAULT_MAX_BYTES = 30 * 1024;

    private ToolTextUtils() {
    }

    static String truncateHead(String text) {
        return truncate(text, DEFAULT_MAX_LINES, DEFAULT_MAX_BYTES, false);
    }

    static String truncateTail(String text) {
        return truncate(text, DEFAULT_MAX_LINES, DEFAULT_MAX_BYTES, true);
    }

    private static String truncate(String text, int maxLines, int maxBytes, boolean keepTail) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>(List.of(text.split("\\R", -1)));
        boolean truncated = false;
        if (lines.size() > maxLines) {
            truncated = true;
            lines = keepTail
                    ? new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()))
                    : new ArrayList<>(lines.subList(0, maxLines));
        }

        String joined = String.join("\n", lines);
        while (joined.getBytes(StandardCharsets.UTF_8).length > maxBytes && lines.size() > 1) {
            truncated = true;
            if (keepTail) {
                lines.remove(0);
            } else {
                lines.remove(lines.size() - 1);
            }
            joined = String.join("\n", lines);
        }

        if (!truncated) {
            return text;
        }
        return joined + "\n\n[Output truncated]";
    }
}
