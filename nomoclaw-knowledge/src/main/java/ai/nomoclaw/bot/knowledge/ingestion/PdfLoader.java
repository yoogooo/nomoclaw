package ai.nomoclaw.bot.knowledge.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads PDF text with page coordinates so preprocessors can remove layout artifacts.
 */
@Component
public class PdfLoader {

    public PdfDocument load(PDDocument document, DocumentParser.PageProgress progress) throws IOException {
        CoordinateTextStripper stripper = new CoordinateTextStripper(progress);
        stripper.setSortByPosition(true);
        stripper.getText(document);
        return new PdfDocument(stripper.pages());
    }

    public record PdfDocument(List<PdfPage> pages) {
        public List<TextElement> elements() {
            return pages.stream().flatMap(page -> page.elements().stream()).toList();
        }
    }

    public record PdfPage(int number, float width, float height, List<TextElement> elements) {
    }

    public record TextElement(String id, int page, int lineIndex, String text, float x, float y,
                              float width, float height, float fontSize, float rotation,
                              float pageWidth, float pageHeight) {
        public String normalizedText(boolean normalizeSpacing) {
            String value = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
            if (normalizeSpacing) {
                value = value.replaceAll("\\s+", "");
            } else {
                value = value.replaceAll("[ \\t]+", " ");
            }
            return value.toLowerCase(Locale.ROOT);
        }
    }

    private static final class CoordinateTextStripper extends PDFTextStripper {
        private final DocumentParser.PageProgress progress;
        private final Map<Integer, List<TextElement>> pageElements = new HashMap<>();
        private final Map<Integer, Integer> lineIndexes = new HashMap<>();
        private int totalPages;

        private CoordinateTextStripper(DocumentParser.PageProgress progress) throws IOException {
            this.progress = progress == null ? (processed, total) -> {
            } : progress;
        }

        private List<PdfPage> pages() {
            List<PdfPage> pages = new ArrayList<>();
            for (int page = 1; page <= totalPages; page++) {
                List<TextElement> elements = pageElements.getOrDefault(page, List.of()).stream()
                        .sorted(Comparator.comparingInt(TextElement::lineIndex)
                                .thenComparing(TextElement::x))
                        .toList();
                float width = elements.stream().findFirst().map(TextElement::pageWidth).orElse(0F);
                float height = elements.stream().findFirst().map(TextElement::pageHeight).orElse(0F);
                pages.add(new PdfPage(page, width, height, elements));
            }
            return pages;
        }

        @Override
        protected void startDocument(PDDocument document) throws IOException {
            totalPages = document.getNumberOfPages();
            super.startDocument(document);
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            progress.accept(getCurrentPageNo(), totalPages);
            super.endPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (text == null || text.isBlank() || textPositions == null || textPositions.isEmpty()) {
                return;
            }
            int page = getCurrentPageNo();
            int lineIndex = lineIndexes.merge(page, 1, Integer::sum);
            PDPage currentPage = getCurrentPage();
            float pageWidth = currentPage.getMediaBox().getWidth();
            float pageHeight = currentPage.getMediaBox().getHeight();
            List<TextElement> elements = pageElements.computeIfAbsent(page, ignored -> new ArrayList<>());
            int offset = 0;
            for (TextPosition position : textPositions) {
                String value = position.getUnicode();
                if (value == null) {
                    offset++;
                    continue;
                }
                String id = page + ":" + lineIndex + ":" + offset;
                TextElement element = new TextElement(id, page, lineIndex, value, position.getXDirAdj(),
                        position.getYDirAdj(), Math.max(0F, position.getWidthDirAdj()),
                        Math.max(0F, position.getHeightDir()), position.getFontSizeInPt(), position.getDir(),
                        pageWidth, pageHeight);
                elements.add(element);
                offset++;
            }
        }
    }
}
