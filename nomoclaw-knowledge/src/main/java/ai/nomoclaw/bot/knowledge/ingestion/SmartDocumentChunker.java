package ai.nomoclaw.bot.knowledge.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Structure-first chunker that treats a document node as a hard boundary.
 */
@Component
public class SmartDocumentChunker implements DocumentChunker {
    static final int TARGET_TOKENS = 500;
    static final int OVERLAP_TOKENS = 80;
    static final int ABSOLUTE_MAX_TOKENS = 2000;
    private static final double SOFT_OVERFLOW_RATIO = 1.25D;

    private final TokenEstimator tokenEstimator;

    public SmartDocumentChunker(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public List<Chunk> split(DocumentParser.ParsedDocument document, int ignoredSizeTokens,
                             int ignoredOverlapTokens) {
        List<Chunk> chunks = new ArrayList<>();
        split(document, ignoredSizeTokens, ignoredOverlapTokens, chunks::add);
        return chunks;
    }

    @Override
    public void split(DocumentParser.ParsedDocument document, int ignoredSizeTokens, int ignoredOverlapTokens,
                      Consumer<Chunk> consumer) {
        List<DocumentParser.DocumentBlock> pending = new ArrayList<>();
        String nodeKey = null;
        int[] index = {0};
        for (DocumentParser.DocumentBlock block : document.blocks()) {
            if (block.text() == null || block.text().isBlank()) continue;
            if (block.type() == DocumentParser.BlockType.HEADING) continue;
            if (nodeKey != null && !nodeKey.equals(block.nodeKey())) {
                emitNode(pending, index, consumer);
                pending.clear();
            }
            nodeKey = block.nodeKey();
            pending.add(block);
        }
        emitNode(pending, index, consumer);
    }

    private void emitNode(List<DocumentParser.DocumentBlock> blocks, int[] index, Consumer<Chunk> consumer) {
        if (blocks.isEmpty()) return;
        List<DocumentParser.DocumentBlock> current = new ArrayList<>();
        int tokens = 0;
        for (DocumentParser.DocumentBlock block : blocks) {
            int blockTokens = tokenEstimator.estimate(block.text());
            if (!current.isEmpty() && tokens + blockTokens > TARGET_TOKENS) {
                emitBlocks(current, index, consumer);
                current.clear();
                tokens = 0;
            }
            if (blockTokens > TARGET_TOKENS * SOFT_OVERFLOW_RATIO) {
                if (!current.isEmpty()) {
                    emitBlocks(current, index, consumer);
                    current.clear();
                    tokens = 0;
                }
                splitOversized(block, index, consumer);
            } else {
                current.add(block);
                tokens += blockTokens;
            }
        }
        emitBlocks(current, index, consumer);
    }

    private void splitOversized(DocumentParser.DocumentBlock block, int[] index, Consumer<Chunk> consumer) {
        if (block.type() == DocumentParser.BlockType.TABLE) {
            splitTable(block, index, consumer);
            return;
        }
        List<String> units = semanticUnits(block);
        List<String> current = new ArrayList<>();
        int tokens = 0;
        for (String unit : units) {
            int unitTokens = tokenEstimator.estimate(unit);
            if (unitTokens > ABSOLUTE_MAX_TOKENS) {
                if (!current.isEmpty()) {
                    emitText(block, String.join(separator(block), current), index, consumer);
                    current.clear();
                    tokens = 0;
                }
                forceSplit(block, unit, index, consumer);
            } else {
                if (!current.isEmpty() && tokens + unitTokens > TARGET_TOKENS) {
                    emitText(block, String.join(separator(block), current), index, consumer);
                    current.clear();
                    tokens = 0;
                }
                current.add(unit);
                tokens += unitTokens;
            }
        }
        if (!current.isEmpty()) emitText(block, String.join(separator(block), current), index, consumer);
    }

    private void splitTable(DocumentParser.DocumentBlock block, int[] index, Consumer<Chunk> consumer) {
        List<String> rows = block.text().lines().filter(line -> !line.isBlank()).toList();
        if (rows.size() < 2) {
            forceSplit(block, block.text(), index, consumer);
            return;
        }
        String header = rows.getFirst();
        List<String> current = new ArrayList<>();
        current.add(header);
        int tokens = tokenEstimator.estimate(header);
        for (String row : rows.subList(1, rows.size())) {
            int rowTokens = tokenEstimator.estimate(row);
            if (current.size() > 1 && tokens + rowTokens > TARGET_TOKENS) {
                emitText(block, String.join("\n", current), index, consumer);
                current.clear();
                current.add(header);
                tokens = tokenEstimator.estimate(header);
            }
            if (tokens + rowTokens > ABSOLUTE_MAX_TOKENS) {
                if (current.size() > 1) {
                    emitText(block, String.join("\n", current), index, consumer);
                    current.clear();
                    current.add(header);
                }
                forceSplit(block, header + "\n" + row, index, consumer);
                tokens = tokenEstimator.estimate(header);
            } else {
                current.add(row);
                tokens += rowTokens;
            }
        }
        if (current.size() > 1) emitText(block, String.join("\n", current), index, consumer);
    }

    private List<String> semanticUnits(DocumentParser.DocumentBlock block) {
        if (block.type() == DocumentParser.BlockType.TABLE || block.type() == DocumentParser.BlockType.LIST) {
            return block.text().lines().filter(line -> !line.isBlank()).toList();
        }
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int offset = 0; offset < block.text().length(); offset++) {
            char value = block.text().charAt(offset);
            if (value == '。' || value == '！' || value == '？' || value == '.' || value == '!' || value == '?') {
                sentences.add(block.text().substring(start, offset + 1).trim());
                start = offset + 1;
            }
        }
        if (start < block.text().length()) sentences.add(block.text().substring(start).trim());
        return sentences.stream().filter(value -> !value.isBlank()).toList();
    }

    private void forceSplit(DocumentParser.DocumentBlock block, String text, int[] index, Consumer<Chunk> consumer) {
        int start = 0;
        while (start < text.length()) {
            int end = tokenBound(text, start, ABSOLUTE_MAX_TOKENS);
            emitText(block, text.substring(start, end), index, consumer);
            if (end >= text.length()) break;
            start = overlapStart(text, start, end, OVERLAP_TOKENS);
        }
    }

    private void emitBlocks(List<DocumentParser.DocumentBlock> blocks, int[] index, Consumer<Chunk> consumer) {
        if (blocks.isEmpty()) return;
        DocumentParser.DocumentBlock first = blocks.getFirst();
        DocumentParser.DocumentBlock last = blocks.getLast();
        String content = String.join("\n\n", blocks.stream().map(DocumentParser.DocumentBlock::text).toList()).trim();
        if (content.isBlank()) return;
        consumer.accept(new Chunk(index[0]++, content, tokenEstimator.estimate(content), first.pageFrom(),
                last.pageTo(), first.sectionPath(), first.charStart(), last.charEnd(), first.nodeKey()));
    }

    private void emitText(DocumentParser.DocumentBlock block, String text, int[] index, Consumer<Chunk> consumer) {
        String content = text.trim();
        if (content.isBlank()) return;
        consumer.accept(new Chunk(index[0]++, content, tokenEstimator.estimate(content), block.pageFrom(),
                block.pageTo(), block.sectionPath(), block.charStart(), block.charEnd(), block.nodeKey()));
    }

    private String separator(DocumentParser.DocumentBlock block) {
        return block.type() == DocumentParser.BlockType.PARAGRAPH ? "" : "\n";
    }

    private int tokenBound(String text, int start, int maxTokens) {
        int low = start + 1;
        int high = text.length();
        int best = low;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (tokenEstimator.estimate(text.substring(start, middle)) <= maxTokens) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private int overlapStart(String text, int chunkStart, int chunkEnd, int overlapTokens) {
        int start = chunkEnd;
        while (start > chunkStart && tokenEstimator.estimate(text.substring(start - 1, chunkEnd)) <= overlapTokens) {
            start--;
        }
        return Math.max(chunkStart + 1, start);
    }
}
