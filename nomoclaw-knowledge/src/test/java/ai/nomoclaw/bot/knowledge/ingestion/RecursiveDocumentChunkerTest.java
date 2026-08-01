package ai.nomoclaw.bot.knowledge.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecursiveDocumentChunkerTest {
    private final RecursiveDocumentChunker chunker = new RecursiveDocumentChunker(new UnicodeTokenEstimator());

    @Test
    void mergesShortConsecutiveParagraphsIntoOneChunk() {
        DocumentParser.ParsedDocument document = new DocumentParser.ParsedDocument(
                List.of(new DocumentParser.Page(1, "", "")),
                List.of(
                        block(1, "", "第一段测试内容用于验证相邻短段落能够被合并为同一个分块。"),
                        block(1, "", "第二段测试内容用于验证合并后的文本保留原有段落分隔符。"),
                        block(1, "", "第三段测试内容用于验证合并后的分块不会丢失连续文本。")),
                List.of());

        List<DocumentChunker.Chunk> chunks = chunker.split(document, 500, 80);

        assertEquals(1, chunks.size());
        assertEquals("第一段测试内容用于验证相邻短段落能够被合并为同一个分块。\n\n"
                + "第二段测试内容用于验证合并后的文本保留原有段落分隔符。\n\n"
                + "第三段测试内容用于验证合并后的分块不会丢失连续文本。", chunks.getFirst().content());
        assertEquals(1, chunks.getFirst().pageFrom());
    }

    @Test
    void allowsTokenChunkAcrossSectionsAndFallsBackToRootMetadata() {
        DocumentParser.ParsedDocument document = new DocumentParser.ParsedDocument(
                List.of(new DocumentParser.Page(1, "", ""), new DocumentParser.Page(2, "", "")),
                List.of(
                        block(1, "Section A", "node-a", "Generated paragraph one for neutral chunk testing."),
                        block(1, "Section A", "node-a", "Generated paragraph two remains adjacent."),
                        block(1, "Section B", "node-b", "Generated content belongs to another section."),
                        block(2, "Section B", "node-b", "Generated continuation appears on the next page.")),
                List.of());

        List<DocumentChunker.Chunk> chunks = chunker.split(document, 500, 80);

        assertEquals(1, chunks.size());
        assertEquals(1, chunks.getFirst().pageFrom());
        assertEquals(2, chunks.getFirst().pageTo());
        assertEquals("root", chunks.getFirst().nodeKey());
        assertEquals("", chunks.getFirst().section());
    }

    private DocumentParser.DocumentBlock block(int page, String section, String text) {
        return new DocumentParser.DocumentBlock(DocumentParser.BlockType.PARAGRAPH, page, section, text);
    }

    private DocumentParser.DocumentBlock block(int page, String section, String nodeKey, String text) {
        return new DocumentParser.DocumentBlock(DocumentParser.BlockType.PARAGRAPH, page, page, section, nodeKey,
                text, 0, text.length(), DocumentParser.BlockStyle.empty(), false);
    }
}
