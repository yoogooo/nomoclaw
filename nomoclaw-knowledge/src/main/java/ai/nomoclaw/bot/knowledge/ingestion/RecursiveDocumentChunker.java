package ai.nomoclaw.bot.knowledge.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Paragraph-aware chunker using a conservative four-characters-per-token estimate.
 */
@Component
public class RecursiveDocumentChunker implements DocumentChunker {
    @Override
    public List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens) {
        int maxChars = Math.max(200, sizeTokens * 4);
        int overlapChars = Math.max(0, Math.min(maxChars / 2, overlapTokens * 4));
        List<Chunk> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DocumentParser.Page page : document.pages()) {
            String text = page.text();
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(text.length(), start + maxChars);
                if (end < text.length()) {
                    int boundary = Math.max(text.lastIndexOf("\n\n", end), text.lastIndexOf('。', end));
                    if (boundary > start + maxChars / 2) end = boundary + 1;
                }
                String content = text.substring(start, end).trim();
                if (content.length() >= 20 && seen.add(content))
                    result.add(new Chunk(result.size(), content, Math.max(1, content.length() / 4), page.number(), page.number(), page.section(), start, end));
                if (end >= text.length()) break;
                start = Math.max(start + 1, end - overlapChars);
            }
        }
        return result;
    }
}
