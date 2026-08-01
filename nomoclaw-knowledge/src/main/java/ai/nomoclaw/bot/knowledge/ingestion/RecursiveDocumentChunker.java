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
        for (ChunkSource source : mergeAdjacentBlocks(document.blocks())) {
            String text = source.text();
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
                            source.pageAt(start), source.pageAt(end - 1), source.sectionPath(start, end),
                            source.charStart() + start, source.charStart() + end, source.nodeKey(start, end)));
                }
                if (end >= text.length()) break;
                start = Math.max(start + 1, overlapStart(text, start, end, overlapTokens));
            }
        }
    }

    /**
     * Joins consecutive structural blocks without making section changes into token chunk boundaries.
     * Structural blocks are parsing hints, rather than hard chunk boundaries: treating every
     * PDF paragraph as a final chunk makes ordinary layout gaps produce tiny embeddings.
     * A paragraph can continue on a following PDF page, so page changes are retained as
     * citation metadata instead of being treated as a split boundary.
     */
    private List<ChunkSource> mergeAdjacentBlocks(List<DocumentParser.DocumentBlock> blocks) {
        List<ChunkSource> sources = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int charStart = 0;
        List<ContextBoundary> boundaries = new ArrayList<>();
        for (DocumentParser.DocumentBlock block : blocks) {
            if (block.text() == null || block.text().isBlank()) continue;
            if (text.isEmpty()) {
                charStart = block.charStart();
            } else {
                text.append("\n\n");
            }
            boundaries.add(new ContextBoundary(text.length(), block.page(), block.sectionPath(), block.nodeKey()));
            text.append(block.text().trim());
        }
        if (!text.isEmpty()) {
            sources.add(new ChunkSource(text.toString(), charStart, List.copyOf(boundaries)));
        }
        return sources;
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

    private record ChunkSource(String text, int charStart, List<ContextBoundary> boundaries) {
        private int pageAt(int offset) {
            ContextBoundary boundary = boundaries.getFirst();
            for (ContextBoundary candidate : boundaries) {
                if (candidate.charStart() > offset) break;
                boundary = candidate;
            }
            return boundary.page();
        }

        private String nodeKey(int start, int end) {
            ContextBoundary first = contextAt(start);
            return boundaries.stream()
                    .filter(boundary -> boundary.charStart() > start && boundary.charStart() < end)
                    .allMatch(boundary -> boundary.nodeKey().equals(first.nodeKey())) ? first.nodeKey() : "root";
        }

        private String sectionPath(int start, int end) {
            return "root".equals(nodeKey(start, end)) ? "" : contextAt(start).sectionPath();
        }

        private ContextBoundary contextAt(int offset) {
            ContextBoundary result = boundaries.getFirst();
            for (ContextBoundary boundary : boundaries) {
                if (boundary.charStart() > offset) break;
                result = boundary;
            }
            return result;
        }
    }

    private record ContextBoundary(int charStart, int page, String sectionPath, String nodeKey) {
    }
}
