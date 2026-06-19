package ai.nomoclaw.bot.channel.platform.sender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class DingTalkMarkdownFormatter {

    private static final int MAX_TITLE_LENGTH = 64;
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?[-]{3,}:?\\s*(\\|\\s*:?[-]{3,}:?\\s*)+\\|?\\s*$");

    private DingTalkMarkdownFormatter() {
    }

    public static String extractTitle(String input) {
        if (input == null || input.isBlank()) {
            return "NomoClaw";
        }
        String normalized = input.replace("\r\n", "\n");
        for (String line : normalized.split("\n")) {
            String title = simplifyInlineMarkdown(line);
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

    public static String formatMarkdown(String input) {
        if (input == null || input.isBlank()) {
            return "暂无内容";
        }
        List<String> lines = List.of(input.replace("\r\n", "\n").split("\n", -1));
        List<String> out = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (isTableRow(line) && index + 1 < lines.size() && TABLE_SEPARATOR.matcher(lines.get(index + 1)).matches()) {
                index = appendTableAsList(lines, index, out);
                continue;
            }
            out.add(normalizeLine(line));
            index++;
        }
        String markdown = String.join("\n", out)
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return markdown.isBlank() ? "暂无内容" : markdown;
    }

    public static String formatPlainText(String input) {
        return MarkdownToReadableTextFormatter.format(input);
    }

    private static int appendTableAsList(List<String> lines, int start, List<String> out) {
        List<String> headers = splitTableRow(lines.get(start));
        int index = start + 2;
        while (index < lines.size() && isTableRow(lines.get(index))) {
            List<String> row = splitTableRow(lines.get(index));
            if (!row.isEmpty()) {
                StringBuilder item = new StringBuilder("- ");
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
                out.add(item.toString());
            }
            index++;
        }
        return index;
    }

    private static String normalizeLine(String line) {
        String text = line == null ? "" : line;
        if (text.stripLeading().startsWith("```")) {
            return "```";
        }
        return text.replace("\t", "    ").stripTrailing();
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

    private static String simplifyInlineMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String out = text.trim();
        out = out.replaceAll("^\\s{0,3}#{1,6}\\s*", "");
        out = out.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        out = out.replaceAll("__(.+?)__", "$1");
        out = out.replaceAll("`([^`]+)`", "$1");
        out = out.replaceAll("\\[(.+?)]\\((.+?)\\)", "$1");
        return out.trim();
    }
}
