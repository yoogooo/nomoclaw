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

    record DocumentBlock(BlockType type, int pageFrom, int pageTo, String sectionPath, String nodeKey, String text,
                         int charStart, int charEnd, BlockStyle style, boolean crossPageContinuation) {
        public DocumentBlock(BlockType type, int page, String sectionPath, String text) {
            this(type, page, page, sectionPath, "root", text, 0, text == null ? 0 : text.length(),
                    BlockStyle.empty(), false);
        }

        public int page() {
            return pageFrom;
        }
    }

    record ParsedDocument(List<Page> pages, List<DocumentBlock> blocks, List<StructureNode> nodes,
                          List<String> warnings) {
        public ParsedDocument(List<Page> pages, List<DocumentBlock> blocks, List<String> warnings) {
            this(pages, blocks, List.of(StructureNode.root(pages)), warnings);
        }

        public ParsedDocument(List<Page> pages) {
            this(pages, pages.stream().map(page -> new DocumentBlock(
                    BlockType.PARAGRAPH, page.number(), page.section(), page.text())).toList(),
                    List.of(StructureNode.root(pages)), List.of());
        }
    }

    record StructureNode(String nodeKey, String parentNodeKey, String type, int level, String code, String title,
                         String sectionPath, int pageFrom, int pageTo, int charStart, int charEnd,
                         String detectionSource, double confidence, boolean indexable, String metadataJson) {
        public static StructureNode root(List<Page> pages) {
            int pageFrom = pages == null || pages.isEmpty() ? 1 : pages.getFirst().number();
            int pageTo = pages == null || pages.isEmpty() ? pageFrom : pages.getLast().number();
            return new StructureNode("root", "", "ROOT", 0, "", "", "", pageFrom, pageTo,
                    0, pages == null ? 0 : pages.stream().mapToInt(page -> page.text().length()).sum(),
                    "FALLBACK", 0D, true, "{}");
        }
    }

    record BlockStyle(float fontSize, boolean bold, float x, float y, float lineHeight) {
        public static BlockStyle empty() {
            return new BlockStyle(0F, false, 0F, 0F, 0F);
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

    record PdfPreprocessingOptions(boolean removeHeader, boolean removeFooter, boolean removeWatermark,
                                   boolean removeTableOfContents) {
        public PdfPreprocessingOptions(boolean removeHeader, boolean removeFooter, boolean removeWatermark) {
            this(removeHeader, removeFooter, removeWatermark, false);
        }

        public static PdfPreprocessingOptions disabled() {
            return new PdfPreprocessingOptions(false, false, false, false);
        }
    }
}
