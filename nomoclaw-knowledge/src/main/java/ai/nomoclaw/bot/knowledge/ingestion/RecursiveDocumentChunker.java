package ai.nomoclaw.bot.knowledge.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Paragraph-aware chunker using a conservative four-characters-per-token estimate.
 */
@Component
public class RecursiveDocumentChunker implements DocumentChunker {
    private final TokenEstimator tokenEstimator;

    public RecursiveDocumentChunker(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens) {
        List<Chunk> result = new ArrayList<>();
        split(document, sizeTokens, overlapTokens, result::add);
        return result;
    }

    @Override
    public void split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens,
                      Consumer<Chunk> consumer) {
        Set<String> seen = new LinkedHashSet<>();
        int index = 0;
        for (DocumentParser.DocumentBlock block : document.blocks()) {
            String text = block.text();
            int start = 0;
            while (start < text.length()) {
                int end = tokenBound(text, start, sizeTokens);
                if (end < text.length()) {
                    int boundary = Math.max(text.lastIndexOf("\n\n", end), text.lastIndexOf('。', end));
                    if (boundary > start && tokenEstimator.estimate(text.substring(start, boundary + 1))
                            >= Math.max(1, sizeTokens / 2)) {
                        end = boundary + 1;
                    }
                }
                String content = text.substring(start, end).trim();
                if (content.length() >= 20 && seen.add(content)) {
                    consumer.accept(new Chunk(index++, content, tokenEstimator.estimate(content),
                            block.page(), block.page(), block.sectionPath(), start, end));
                }
                if (end >= text.length()) break;
                start = Math.max(start + 1, overlapStart(text, start, end, overlapTokens));
            }
        }
    }

    private int tokenBound(String text, int start, int sizeTokens) {
        int low = start + 1;
        int high = text.length();
        int best = low;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (tokenEstimator.estimate(text.substring(start, middle)) <= sizeTokens) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private int overlapStart(String text, int chunkStart, int chunkEnd, int overlapTokens) {
        if (overlapTokens <= 0) return chunkEnd;
        int low = chunkStart;
        int high = chunkEnd;
        int best = chunkEnd;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (tokenEstimator.estimate(text.substring(middle, chunkEnd)) <= overlapTokens) {
                best = middle;
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        return best;
    }
}
