package ai.nomoclaw.bot.channel.platform.sender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class FeishuMarkdownFormatter {

    private static final int MAX_TITLE_LENGTH = 64;
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?[-]{3,}:?\\s*(\\|\\s*:?[-]{3,}:?\\s*)+\\|?\\s*$");

    private FeishuMarkdownFormatter() {
    }

    static Map<String, Object> formatPost(String input) {
        String normalized = input == null ? "" : input.replace("\r\n", "\n");
        List<String> lines = List.of(normalized.split("\n", -1));
        List<List<Map<String, String>>> content = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (isTableRow(line) && index + 1 < lines.size() && TABLE_SEPARATOR.matcher(lines.get(index + 1)).matches()) {
                index = appendTableAsParagraphs(lines, index, content);
                continue;
            }
            String text = normalizeLine(line);
            if (!text.isBlank()) {
                content.add(List.of(textTag(text)));
            }
            index++;
        }
        if (content.isEmpty()) {
            content.add(List.of(textTag("暂无内容")));
        }
        return Map.of(
                "zh_cn", Map.of(
                        "title", extractTitle(input),
                        "content", content
                )
        );
    }

    static String extractTitle(String input) {
        if (input == null || input.isBlank()) {
            return "NomoClaw";
        }
        String normalized = input.replace("\r\n", "\n");
        for (String line : normalized.split("\n")) {
            String title = normalizeLine(line);
            if (title.isBlank()) {
                continue;
            }
            if (title.length() > MAX_TITLE_LENGTH) {
                return title.substring(0, MAX_TITLE_LENGTH);
            }
            return title;
        }
        return "NomoClaw";
    }

    private static int appendTableAsParagraphs(List<String> lines, int start, List<List<Map<String, String>>> content) {
        List<String> headers = splitTableRow(lines.get(start));
        int index = start + 2;
        while (index < lines.size() && isTableRow(lines.get(index))) {
            List<String> row = splitTableRow(lines.get(index));
            if (!row.isEmpty()) {
                StringBuilder item = new StringBuilder("• ");
                for (int columnIndex = 0; columnIndex < headers.size() && columnIndex < row.size(); columnIndex++) {
                    String header = simplifyInlineMarkdown(headers.get(columnIndex));
                    String value = simplifyInlineMarkdown(row.get(columnIndex));
                    if (header.isBlank() || value.isBlank()) {
                        continue;
                    }
                    if (item.length() > 2) {
                        item.append("，");
                    }
                    item.append(header).append(": ").append(value);
                }
                if (item.length() == 2) {
                    item.append(simplifyInlineMarkdown(String.join(" ", row)));
                }
                content.add(List.of(textTag(item.toString())));
            }
            index++;
        }
        return index;
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
        String text = line == null ? "" : line.trim();
        if (text.isBlank()) {
            return "";
        }
        if (text.startsWith("```")) {
            return "";
        }
        if (text.matches("^\\s{0,3}#{1,6}\\s+.*$")) {
            return "【" + simplifyInlineMarkdown(text.replaceFirst("^\\s{0,3}#{1,6}\\s+", "")) + "】";
        }
        if (text.matches("^\\s*[-*+]\\s+.*$")) {
            return "• " + simplifyInlineMarkdown(text.replaceFirst("^\\s*[-*+]\\s+", ""));
        }
        return simplifyInlineMarkdown(text);
    }

    private static Map<String, String> textTag(String text) {
        Map<String, String> tag = new LinkedHashMap<>();
        tag.put("tag", "text");
        tag.put("text", text);
        return tag;
    }

    private static String simplifyInlineMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String out = text.trim();
        out = out.replaceAll("\\[(.+?)]\\((.+?)\\)", "$1 ($2)");
        out = out.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        out = out.replaceAll("__(.+?)__", "$1");
        out = out.replaceAll("`([^`]+)`", "$1");
        return out.trim();
    }
}
