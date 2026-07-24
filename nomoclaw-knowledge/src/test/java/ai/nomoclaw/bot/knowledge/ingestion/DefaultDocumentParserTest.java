package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.ingestion.DefaultDocumentParser.KnowledgeParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultDocumentParserTest {
    @TempDir
    Path directory;

    @Test
    void preservesMarkdownStructureAndHeadingPath() throws Exception {
        Path file = directory.resolve("guide.md");
        Files.writeString(file, "# Guide\n\nIntroduction\n\n## Setup\n\n- install\n\n```java\nrun();\n```\n");

        DocumentParser.ParsedDocument parsed = new DefaultDocumentParser().parse(file);

        assertEquals(
                List.of(
                        DocumentParser.BlockType.HEADING,
                        DocumentParser.BlockType.PARAGRAPH,
                        DocumentParser.BlockType.HEADING,
                        DocumentParser.BlockType.LIST,
                        DocumentParser.BlockType.CODE),
                parsed.blocks().stream().map(DocumentParser.DocumentBlock::type).toList());
        assertEquals("Guide / Setup", parsed.blocks().get(3).sectionPath());
    }

    @Test
    void rejectsMalformedUtf8() throws Exception {
        Path file = directory.resolve("invalid.txt");
        Files.write(file, new byte[]{(byte) 0xC3, (byte) 0x28});

        KnowledgeParseException error = assertThrows(
                KnowledgeParseException.class, () -> new DefaultDocumentParser().parse(file));

        assertEquals("INVALID_ENCODING", error.code());
    }

    @Test
    void estimatesCjkAndLatinRunsDifferently() {
        UnicodeTokenEstimator estimator = new UnicodeTokenEstimator();

        assertEquals(5, estimator.estimate("知识库导入"));
        assertEquals(2, estimator.estimate("abcdefgh"));
    }
}
