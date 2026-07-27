package ai.nomoclaw.bot.knowledge.ingestion;

import java.nio.file.Path;
import java.util.List;

/**
 * Extracts structured text from an uploaded document.
 */
public interface DocumentParser {
    /**
     * Returns whether this parser accepts the supplied MIME type or filename.
     */
    boolean supports(String contentType, String fileName);

    /**
     * Extracts ordered pages and their structural context from a stored file.
     */
    ParsedDocument parse(Path file);

    /**
     * Parses while reporting durable page progress when the format exposes page boundaries.
     */
    default ParsedDocument parse(Path file, PageProgress progress) {
        return parse(file, PreprocessingOptions.disabled(), progress);
    }

    /**
     * Parses with optional preprocessing selected for this import.
     */
    default ParsedDocument parse(Path file, PreprocessingOptions preprocessing, PageProgress progress) {
        ParsedDocument parsed = parse(file);
        progress.accept(parsed.pages().size(), parsed.pages().size());
        return parsed;
    }

    @FunctionalInterface
    interface PageProgress {
        void accept(int processedPages, int totalPages);
    }

    /**
     * Signals that the caller lost ownership and parsing must stop at the next page boundary.
     */
    final class ParsingAbortedException extends RuntimeException {
        public ParsingAbortedException() {
            super("Document parsing aborted");
        }
    }

    enum BlockType {
        HEADING, PARAGRAPH, TABLE, LIST, CODE
    }

    record DocumentBlock(BlockType type, int page, String sectionPath, String text) {
    }

    record ParsedDocument(List<Page> pages, List<DocumentBlock> blocks, List<String> warnings) {
        public ParsedDocument(List<Page> pages) {
            this(pages, pages.stream().map(page -> new DocumentBlock(
                    BlockType.PARAGRAPH, page.number(), page.section(), page.text())).toList(), List.of());
        }
    }

    record Page(int number, String section, String text) {
    }

    record PreprocessingOptions(boolean enabled, PdfPreprocessingOptions pdf) {
        public static PreprocessingOptions disabled() {
            return new PreprocessingOptions(false, PdfPreprocessingOptions.disabled());
        }

        public PdfPreprocessingOptions pdfOptions() {
            return pdf == null ? PdfPreprocessingOptions.disabled() : pdf;
        }
    }

    record PdfPreprocessingOptions(boolean removeHeader, boolean removeFooter, boolean removeWatermark) {
        public static PdfPreprocessingOptions disabled() {
            return new PdfPreprocessingOptions(false, false, false);
        }
    }
}
