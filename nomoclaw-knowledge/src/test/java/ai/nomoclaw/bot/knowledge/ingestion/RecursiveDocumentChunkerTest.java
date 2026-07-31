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
    void mergesAcrossPagesButKeepsSectionBoundariesAndPageRanges() {
        DocumentParser.ParsedDocument document = new DocumentParser.ParsedDocument(
                List.of(new DocumentParser.Page(1, "", ""), new DocumentParser.Page(2, "", "")),
                List.of(
                        block(1, "第一部分", "第一部分的第一段内容足够用于测试分块的页面与章节边界。"),
                        block(1, "第一部分", "第一部分的第二段内容不应被拆成单独的很小分块。"),
                        block(1, "第二部分", "第二部分的独立内容不应与第一部分合并，且应保留准确的章节信息。"),
                        block(2, "第二部分", "下一页的独立内容不应与上一页合并，且应保留准确的页码信息。")),
                List.of());

        List<DocumentChunker.Chunk> chunks = chunker.split(document, 500, 80);

        assertEquals(2, chunks.size());
        assertEquals(List.of(1, 1), chunks.stream().map(DocumentChunker.Chunk::pageFrom).toList());
        assertEquals(List.of(1, 2), chunks.stream().map(DocumentChunker.Chunk::pageTo).toList());
        assertEquals(List.of("第一部分", "第二部分"),
                chunks.stream().map(DocumentChunker.Chunk::section).toList());
    }

    private DocumentParser.DocumentBlock block(int page, String section, String text) {
        return new DocumentParser.DocumentBlock(DocumentParser.BlockType.PARAGRAPH, page, section, text);
    }
}
