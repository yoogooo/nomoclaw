package ai.nomoclaw.bot.knowledge.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartDocumentChunkerTest {
    private final SmartDocumentChunker chunker = new SmartDocumentChunker(new UnicodeTokenEstimator());

    @Test
    void keepsShortSiblingSectionsInSeparateChunks() {
        DocumentParser.ParsedDocument document = document(List.of(
                block("section-1", "1.1 Overview", 1, 1, "Short content for the first section."),
                block("section-2", "1.2 Parameters", 1, 1, "Short content for the second section.")));

        List<DocumentChunker.Chunk> chunks = chunker.split(document, 100, 20);

        assertEquals(2, chunks.size());
        assertEquals(List.of("section-1", "section-2"),
                chunks.stream().map(DocumentChunker.Chunk::nodeKey).toList());
        assertFalse(chunks.getFirst().content().contains("second section"));
    }

    @Test
    void mergesBlocksInOneSectionAcrossPagesAndPreservesPageRange() {
        DocumentParser.ParsedDocument document = document(List.of(
                block("section-1", "1.1 Overview", 1, 1, "The first part continues on the next page."),
                block("section-1", "1.1 Overview", 2, 2, "The second part completes the same section.")));

        List<DocumentChunker.Chunk> chunks = chunker.split(document, 100, 20);

        assertEquals(1, chunks.size());
        assertEquals(1, chunks.getFirst().pageFrom());
        assertEquals(2, chunks.getFirst().pageTo());
    }

    @Test
    void keepsSmallSectionTailWithPreviousChunkWithinSoftOverflowLimit() {
        SmartDocumentChunker characterChunker = new SmartDocumentChunker(String::length);
        DocumentParser.ParsedDocument document = document(List.of(
                block("section-1", "3.1 Details", 1, 1, "a".repeat(481)),
                block("section-1", "3.1 Details", 2, 2, "b".repeat(87))));

        List<DocumentChunker.Chunk> chunks = characterChunker.split(document, 100, 20);

        assertEquals(1, chunks.size());
        assertEquals(570, chunks.getFirst().tokenCount());
        assertEquals(1, chunks.getFirst().pageFrom());
        assertEquals(2, chunks.getFirst().pageTo());
    }

    @Test
    void repeatsTableHeaderWhenRowsRequireMultipleChunks() {
        SmartDocumentChunker characterChunker = new SmartDocumentChunker(String::length);
        String header = "Column A | Column B";
        StringBuilder table = new StringBuilder(header);
        for (int index = 0; index < 80; index++) {
            table.append('\n').append("entry-").append(index)
                    .append(" | neutral generated value for chunking");
        }
        DocumentParser.DocumentBlock tableBlock = new DocumentParser.DocumentBlock(
                DocumentParser.BlockType.TABLE, 1, 1, "1.1 Data", "section-1", table.toString(),
                0, table.length(), DocumentParser.BlockStyle.empty(), false);

        List<DocumentChunker.Chunk> chunks = characterChunker.split(document(List.of(tableBlock)), 100, 20);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().startsWith(header + "\n")));
    }

    private DocumentParser.ParsedDocument document(List<DocumentParser.DocumentBlock> blocks) {
        return new DocumentParser.ParsedDocument(
                List.of(new DocumentParser.Page(1, "", ""), new DocumentParser.Page(2, "", "")),
                blocks, List.of());
    }

    private DocumentParser.DocumentBlock block(String nodeKey, String path, int pageFrom, int pageTo, String text) {
        return new DocumentParser.DocumentBlock(DocumentParser.BlockType.PARAGRAPH, pageFrom, pageTo, path,
                nodeKey, text, 0, text.length(), DocumentParser.BlockStyle.empty(), pageFrom != pageTo);
    }
}
