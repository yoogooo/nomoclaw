package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.config.KnowledgePropertiesTestSupport;
import ai.nomoclaw.bot.knowledge.ingestion.DefaultDocumentParser.KnowledgeParseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void appliesSelectedPdfPreprocessing() throws Exception {
        Path file = directory.resolve("handbook.pdf");
        writePdf(file);
        KnowledgeProperties properties = KnowledgePropertiesTestSupport.properties();
        properties.getParsing().getPdf().getPreprocessing().setWatermarkLines(List.of("CONFIDENTIAL"));
        DefaultDocumentParser parser = new DefaultDocumentParser(properties);

        DocumentParser.ParsedDocument parsed = parser.parse(file, new DocumentParser.PreprocessingOptions(true,
                new DocumentParser.PdfPreprocessingOptions(true, true, true)), (processed, total) -> {
        });
        String text = parsed.pages().stream().map(DocumentParser.Page::text).reduce("", String::concat);

        assertFalse(text.contains("Company Handbook"));
        assertFalse(text.contains("Internal Use Only"));
        assertFalse(text.contains("CONFIDENTIAL"));
        assertTrue(text.contains("Meal allowance policy"));
        assertTrue(parsed.warnings().contains("PDF_PREPROCESSING_APPLIED"));
    }

    @Test
    void removesRepeatedCenteredTextWatermarkAutomatically() throws Exception {
        Path file = directory.resolve("watermarked.pdf");
        writeWatermarkedPdf(file, true);
        DefaultDocumentParser parser = new DefaultDocumentParser(KnowledgePropertiesTestSupport.properties());

        DocumentParser.ParsedDocument parsed = parser.parse(file, new DocumentParser.PreprocessingOptions(true,
                new DocumentParser.PdfPreprocessingOptions(false, false, true)), (processed, total) -> {
        });
        String text = parsed.pages().stream().map(DocumentParser.Page::text).reduce("", String::concat);

        assertFalse(text.contains("DRAFT WATERMARK"));
        assertTrue(text.contains("Meal allowance policy"));
        assertTrue(text.contains("Attendance policy"));
        assertTrue(parsed.warnings().contains("PDF_PREPROCESSING_APPLIED"));
    }

    @Test
    void keepsSingleOccurrenceTextWhenWatermarkRemovalIsEnabled() throws Exception {
        Path file = directory.resolve("single-watermark-like-text.pdf");
        writeWatermarkedPdf(file, false);
        DefaultDocumentParser parser = new DefaultDocumentParser(KnowledgePropertiesTestSupport.properties());

        DocumentParser.ParsedDocument parsed = parser.parse(file, new DocumentParser.PreprocessingOptions(true,
                new DocumentParser.PdfPreprocessingOptions(false, false, true)), (processed, total) -> {
        });
        String text = parsed.pages().stream().map(DocumentParser.Page::text).reduce("", String::concat);

        assertTrue(text.contains("DRAFT WATERMARK"));
        assertTrue(text.contains("Attendance policy"));
    }

    @Test
    void keepsDetectedWatermarkWhenWatermarkRemovalIsDisabled() throws Exception {
        Path file = directory.resolve("watermark-disabled.pdf");
        writeWatermarkedPdf(file, true);
        DefaultDocumentParser parser = new DefaultDocumentParser(KnowledgePropertiesTestSupport.properties());

        DocumentParser.ParsedDocument parsed = parser.parse(file, new DocumentParser.PreprocessingOptions(true,
                new DocumentParser.PdfPreprocessingOptions(true, true, false)), (processed, total) -> {
        });
        String text = parsed.pages().stream().map(DocumentParser.Page::text).reduce("", String::concat);

        assertTrue(text.contains("DRAFT WATERMARK"));
    }

    @Test
    void removesHeaderAndFooterWithoutRemovingWatermark() throws Exception {
        Path file = directory.resolve("header-footer-only.pdf");
        writeWatermarkedPdf(file, true);
        DefaultDocumentParser parser = new DefaultDocumentParser(KnowledgePropertiesTestSupport.properties());

        DocumentParser.ParsedDocument parsed = parser.parse(file, new DocumentParser.PreprocessingOptions(true,
                new DocumentParser.PdfPreprocessingOptions(true, true, false)), (processed, total) -> {
        });
        String text = parsed.pages().stream().map(DocumentParser.Page::text).reduce("", String::concat);

        assertFalse(text.contains("Company Handbook"));
        assertFalse(text.contains("Internal Use Only"));
        assertTrue(text.contains("DRAFT WATERMARK"));
    }

    @Test
    void removesNonRepeatedHeaderAndFooterLines() throws Exception {
        Path file = directory.resolve("dynamic-header-footer.pdf");
        writePdfWithDynamicMargins(file);
        DefaultDocumentParser parser = new DefaultDocumentParser(KnowledgePropertiesTestSupport.properties());

        DocumentParser.ParsedDocument parsed = parser.parse(file, new DocumentParser.PreprocessingOptions(true,
                new DocumentParser.PdfPreprocessingOptions(true, true, false)), (processed, total) -> {
        });
        String text = parsed.pages().stream().map(DocumentParser.Page::text).reduce("", String::concat);

        assertFalse(text.contains("Header 1"));
        assertFalse(text.contains("Header 2"));
        assertFalse(text.contains("Footer 1"));
        assertFalse(text.contains("Footer 2"));
        assertTrue(text.contains("First page body"));
        assertTrue(text.contains("Second page body"));
    }

    @Test
    void estimatesCjkAndLatinRunsDifferently() {
        UnicodeTokenEstimator estimator = new UnicodeTokenEstimator();

        assertEquals(5, estimator.estimate("知识库导入"));
        assertEquals(2, estimator.estimate("abcdefgh"));
    }

    private void writePdf(Path file) throws Exception {
        try (PDDocument document = new PDDocument()) {
            addPage(document, "Meal allowance policy");
            addPage(document, "Attendance policy");
            document.save(file.toFile());
        }
    }

    private void writePdfWithDynamicMargins(Path file) throws Exception {
        try (PDDocument document = new PDDocument()) {
            addPageWithMargins(document, "Header 1", "First page body", "Footer 1");
            addPageWithMargins(document, "Header 2", "Second page body", "Footer 2");
            document.save(file.toFile());
        }
    }

    private void addPage(PDDocument document, String body) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(50, 750);
            content.showText("Company Handbook");
            content.newLineAtOffset(0, -80);
            content.showText("CONFIDENTIAL");
            content.newLineAtOffset(0, -80);
            content.showText(body);
            content.newLineAtOffset(0, -420);
            content.showText("Internal Use Only");
            content.endText();
        }
    }

    private void addPageWithMargins(PDDocument document, String header, String body, String footer) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(50, 750);
            content.showText(header);
            content.newLineAtOffset(0, -160);
            content.showText(body);
            content.newLineAtOffset(0, -500);
            content.showText(footer);
            content.endText();
        }
    }

    private void writeWatermarkedPdf(Path file, boolean repeatedWatermark) throws Exception {
        try (PDDocument document = new PDDocument()) {
            addWatermarkedPage(document, "Meal allowance policy", true);
            addWatermarkedPage(document, "Attendance policy", repeatedWatermark);
            document.save(file.toFile());
        }
    }

    private void addWatermarkedPage(PDDocument document, String body, boolean watermark) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(50, 750);
            content.showText("Company Handbook");
            content.newLineAtOffset(0, -100);
            content.showText(body);
            content.newLineAtOffset(0, -470);
            content.showText("Internal Use Only");
            content.endText();
            if (watermark) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 28);
                content.newLineAtOffset(170, 390);
                content.showText("DRAFT WATERMARK");
                content.endText();
            }
        }
    }
}
