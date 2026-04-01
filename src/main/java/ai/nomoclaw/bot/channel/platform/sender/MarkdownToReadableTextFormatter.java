package ai.nomoclaw.bot.channel.platform.sender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class MarkdownToReadableTextFormatter {

    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?[-]{3,}:?\\s*(\\|\\s*:?[-]{3,}:?\\s*)+\\|?\\s*$");

    private MarkdownToReadableTextFormatter() {
    }

    static String format(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        List<String> lines = List.of(input.replace("\r\n", "\n").split("\n", -1));
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (isTableRow(line) && i + 1 < lines.size() && TABLE_SEPARATOR.matcher(lines.get(i + 1)).matches()) {
                i = appendTableAsList(lines, i, out);
                continue;
            }
            out.add(normalizeLine(line));
            i++;
        }
        return cleanupBlankLines(String.join("\n", out));
    }

    private static int appendTableAsList(List<String> lines, int start, List<String> out) {
        List<String> headers = splitTableRow(lines.get(start));
        int i = start + 2;
        while (i < lines.size() && isTableRow(lines.get(i))) {
            List<String> row = splitTableRow(lines.get(i));
            if (!row.isEmpty()) {
                StringBuilder item = new StringBuilder("- ");
                for (int j = 0; j < headers.size() && j < row.size(); j++) {
                    String h = stripMarkdown(headers.get(j));
                    String v = stripMarkdown(row.get(j));
                    if (h.isBlank() || v.isBlank()) {
                        continue;
                    }
                    if (item.length() > 2) {
                        item.append("，");
                    }
                    item.append(h).append(": ").append(v);
                }
                if (item.length() == 2) {
                    item.append(stripMarkdown(String.join(" ", row)));
                }
                out.add(item.toString());
            }
            i++;
        }
        return i;
    }

    private static boolean isTableRow(String line) {
        String text = line == null ? "" : line.trim();
        return text.startsWith("|") && text.endsWith("|") && text.length() >= 2;
    }

    private static List<String> splitTableRow(String row) {
        String text = row == null ? "" : row.trim();
        if (text.startsWith("|")) {
            text = text.substring(1);
        }
        if (text.endsWith("|")) {
            text = text.substring(0, text.length() - 1);
        }
        String[] parts = text.split("\\|", -1);
        List<String> items = new ArrayList<>(parts.length);
        for (String part : parts) {
            items.add(part == null ? "" : part.trim());
        }
        return items;
    }

    private static String normalizeLine(String line) {
        String text = line == null ? "" : line;
        text = text.replaceAll("^\\s{0,3}#{1,6}\\s*", "【");
        if (text.startsWith("【") && !text.endsWith("】") && !text.isBlank()) {
            text = text + "】";
        }
        text = stripMarkdown(text);
        return text;
    }

    private static String stripMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String out = text;
        out = out.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        out = out.replaceAll("__(.+?)__", "$1");
        out = out.replaceAll("`([^`]+)`", "$1");
        out = out.replaceAll("\\[(.+?)]\\((.+?)\\)", "$1");
        return out.trim();
    }

    private static String cleanupBlankLines(String text) {
        String out = text.replaceAll("[ \\t]+$", "");
        out = out.replaceAll("\\n{3,}", "\n\n");
        return out.trim();
    }
}
