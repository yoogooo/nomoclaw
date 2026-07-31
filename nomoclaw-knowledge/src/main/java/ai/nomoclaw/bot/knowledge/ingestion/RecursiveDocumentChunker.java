package ai.nomoclaw.bot.knowledge.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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
                            source.pageAt(start), source.pageAt(end - 1), source.sectionPath(),
                            source.charStart() + start, source.charStart() + end));
                }
                if (end >= text.length()) break;
                start = Math.max(start + 1, overlapStart(text, start, end, overlapTokens));
            }
        }
    }

    /**
     * Joins consecutive structural blocks when they belong to the same section.
     * Structural blocks are parsing hints, rather than hard chunk boundaries: treating every
     * PDF paragraph as a final chunk makes ordinary layout gaps produce tiny embeddings.
     * A paragraph can continue on a following PDF page, so page changes are retained as
     * citation metadata instead of being treated as a split boundary.
     */
    private List<ChunkSource> mergeAdjacentBlocks(List<DocumentParser.DocumentBlock> blocks) {
        List<ChunkSource> sources = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        String sectionPath = "";
        int charStart = 0;
        int nextCharStart = 0;
        List<PageBoundary> pageBoundaries = new ArrayList<>();
        for (DocumentParser.DocumentBlock block : blocks) {
            if (block.text() == null || block.text().isBlank()) continue;
            if (!text.isEmpty() && !Objects.equals(sectionPath, block.sectionPath())) {
                sources.add(new ChunkSource(sectionPath, text.toString(), charStart, List.copyOf(pageBoundaries)));
                nextCharStart += text.length() + 2;
                text.setLength(0);
                pageBoundaries.clear();
            }
            if (text.isEmpty()) {
                sectionPath = block.sectionPath();
                charStart = nextCharStart;
                pageBoundaries.add(new PageBoundary(0, block.page()));
            } else {
                text.append("\n\n");
                if (pageBoundaries.getLast().page() != block.page()) {
                    pageBoundaries.add(new PageBoundary(text.length(), block.page()));
                }
            }
            text.append(block.text().trim());
        }
        if (!text.isEmpty()) {
            sources.add(new ChunkSource(sectionPath, text.toString(), charStart, List.copyOf(pageBoundaries)));
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

    private record ChunkSource(String sectionPath, String text, int charStart, List<PageBoundary> pageBoundaries) {
        private int pageAt(int offset) {
            PageBoundary boundary = pageBoundaries.getFirst();
            for (PageBoundary candidate : pageBoundaries) {
                if (candidate.charStart() > offset) break;
                boundary = candidate;
            }
            return boundary.page();
        }
    }

    private record PageBoundary(int charStart, int page) {
    }
}
