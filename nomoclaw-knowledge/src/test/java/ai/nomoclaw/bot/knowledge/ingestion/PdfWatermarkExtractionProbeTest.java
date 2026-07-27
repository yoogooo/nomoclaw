package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.config.KnowledgePropertiesTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfWatermarkExtractionProbeTest {

    @Test
    void detectsAndRemovesWatermarkTextFromExternalPdf() throws Exception {
        String pdfPath = System.getProperty("knowledge.pdf.fixture", "");
        assumeTrue(!pdfPath.isBlank(), "Set -Dknowledge.pdf.fixture=/path/to/file.pdf to run this probe");

        Path file = Path.of(pdfPath).toAbsolutePath().normalize();
        assumeTrue(Files.isRegularFile(file), "PDF fixture does not exist: " + file);

        PdfLoader loader = new PdfLoader();
        WatermarkDetector detector = new WatermarkDetector();
        TextCleaner cleaner = new TextCleaner(detector);
        KnowledgeProperties properties = KnowledgePropertiesTestSupport.properties();
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PdfLoader.PdfDocument pdf = loader.load(document, (processed, total) -> {
            });
            List<WatermarkDetector.DetectedWatermark> watermarks = detector.detectWatermarks(pdf,
                    properties.getParsing().getPdf().getPreprocessing());
            Set<String> watermarkElementIds = watermarkElementIds(watermarks);
            printDetectedWatermarks(pdf, watermarks, watermarkElementIds);
            String originalText = String.join("\n", pdf.elements().stream()
                    .map(PdfLoader.TextElement::text).toList());
            TextCleaner.PdfCleanupResult cleaned = cleaner.clean(pdf,
                    new DocumentParser.PreprocessingOptions(true,
                            new DocumentParser.PdfPreprocessingOptions(false, false, true)),
                    properties.getParsing().getPdf().getPreprocessing());
            printCleanedPages(cleaned);
            String cleanedText = String.join("\n", cleaned.pages().stream()
                    .map(DocumentParser.Page::text).toList());

            assertFalse(watermarkElementIds.isEmpty(), () -> "No watermark candidate detected. Extracted text:\n"
                    + originalText);
            assertTrue(cleaned.removedCount() > 0, () -> "No text element was removed. Extracted text:\n"
                    + originalText);
            assertTrue(cleanedText.length() < originalText.length(), () -> "Cleaned text was not shorter."
                    + "\nOriginal text:\n" + originalText + "\nCleaned text:\n" + cleanedText);
            assertFalse(cleanedText.isBlank(), "Cleaning removed all extracted text");
        }
    }

    private Set<String> watermarkElementIds(List<WatermarkDetector.DetectedWatermark> watermarks) {
        Set<String> ids = new LinkedHashSet<>();
        watermarks.forEach(watermark -> ids.addAll(watermark.elementIds()));
        return ids;
    }

    private void printDetectedWatermarks(PdfLoader.PdfDocument pdf,
                                         List<WatermarkDetector.DetectedWatermark> watermarks,
                                         Set<String> watermarkElementIds) {
        System.err.println("[PdfWatermarkExtractionProbe] detected watermark candidates:");
        System.err.printf("[PdfWatermarkExtractionProbe] semanticWatermarks=%d detectedElements=%d%n",
                watermarks.size(), watermarkElementIds.size());
        watermarks.forEach(watermark -> System.err.printf(
                "[PdfWatermarkExtractionProbe] text=%s firstPage=%d pageCount=%d confidence=%.2f elements=%d%n",
                printable(watermark.text()), watermark.firstPage(), watermark.pageCount(), watermark.confidence(),
                watermark.elementIds().size()));
    }

    private void printCleanedPages(TextCleaner.PdfCleanupResult cleaned) {
        System.err.printf("[PdfWatermarkExtractionProbe] cleaned removedElements=%d pages=%d%n",
                cleaned.removedCount(), cleaned.pages().size());
        cleaned.pages().forEach(page -> {
            System.err.printf("[PdfWatermarkExtractionProbe] cleaned page=%d%n", page.number());
            System.err.println(page.text());
        });
    }

    private String printable(String text) {
        return text.replace("\r", "\\r").replace("\n", "\\n");
    }
}
