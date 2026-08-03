package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser.Page;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser.PdfPreprocessingOptions;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser.PreprocessingOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes detected PDF layout artifacts from coordinate-level text elements.
 */
@Component
public class TextCleaner {
    private final WatermarkDetector watermarkDetector;

    public TextCleaner(WatermarkDetector watermarkDetector) {
        this.watermarkDetector = watermarkDetector;
    }

    public PdfCleanupResult clean(PdfLoader.PdfDocument document, PreprocessingOptions preprocessing,
                                  KnowledgeProperties.PdfPreprocessing config) {
        if (preprocessing == null || !preprocessing.enabled()) {
            return pages(document, Set.of(), List.of(), config);
        }
        PdfPreprocessingOptions pdf = preprocessing.pdfOptions();
        if (!pdf.removeHeader() && !pdf.removeFooter() && !pdf.removeWatermark() && !pdf.removeTableOfContents()) {
            TableOfContentsDetection detected = detectTableOfContents(document);
            return pages(document, Set.of(), List.of(), config, detected.pages());
        }
        Set<String> removed = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        if (pdf.removeHeader()) {
            removed.addAll(marginElements(document, true, config));
        }
        if (pdf.removeFooter()) {
            removed.addAll(marginElements(document, false, config));
        }
        if (pdf.removeWatermark()) {
            removed.addAll(watermarkDetector.detect(document, config));
            removed.addAll(configuredWatermarkElements(document, config));
        }
        TableOfContentsDetection tableOfContents = detectTableOfContents(document);
        Set<Integer> tableOfContentsPages = tableOfContents.pages();
        if (pdf.removeTableOfContents() && !tableOfContents.elements().isEmpty()) {
                removed.addAll(tableOfContents.elements());
                warnings.add("TABLE_OF_CONTENTS_FILTERED");
        }
        if (!removed.isEmpty()) warnings.add("PDF_PREPROCESSING_APPLIED");
        return pages(document, removed, warnings, config, tableOfContentsPages);
    }

    private TableOfContentsDetection detectTableOfContents(PdfLoader.PdfDocument document) {
        Set<String> removed = new HashSet<>();
        Set<Integer> pages = new HashSet<>();
        for (PdfLoader.PdfPage page : document.pages()) {
            List<TextLine> lines = lines(page.elements());
            if (lines.size() < 5) continue;
            List<TextLine> entries = lines.stream().filter(this::looksLikeTableOfContentsEntry).toList();
            if (entries.size() * 10 < lines.size() * 6) continue;
            float rightEdge = entries.stream().map(this::rightEdge).sorted().skip(entries.size() / 2)
                    .findFirst().orElse(0F);
            long aligned = entries.stream().filter(line -> Math.abs(rightEdge(line) - rightEdge) <= 24F).count();
            if (aligned * 10 < entries.size() * 7) continue;
            page.elements().forEach(element -> removed.add(element.id()));
            pages.add(page.number());
        }
        return new TableOfContentsDetection(Set.copyOf(removed), Set.copyOf(pages));
    }

    private boolean looksLikeTableOfContentsEntry(TextLine line) {
        String text = normalize(line.text());
        if (text.length() < 3 || text.length() > 160) return false;
        int offset = text.length() - 1;
        while (offset >= 0 && Character.isWhitespace(text.charAt(offset))) offset--;
        int digits = 0;
        while (offset >= 0 && Character.isDigit(text.charAt(offset))) {
            digits++;
            offset--;
        }
        return digits > 0 && offset >= 1;
    }

    private float rightEdge(TextLine line) {
        return line.elements().stream().map(element -> element.x() + element.width())
                .max(Float::compareTo).orElse(0F);
    }

    private Set<String> marginElements(PdfLoader.PdfDocument document, boolean header,
                                       KnowledgeProperties.PdfPreprocessing config) {
        int maxLines = Math.max(0, header ? config.getMaxHeaderLines() : config.getMaxFooterLines());
        if (maxLines == 0) return Set.of();
        Set<String> removed = new HashSet<>();
        for (PdfLoader.PdfPage page : document.pages()) {
            List<TextLine> lines = lines(page.elements());
            int size = lines.size();
            for (int offset = 0; offset < Math.min(maxLines, size); offset++) {
                TextLine line = header ? lines.get(offset) : lines.get(size - 1 - offset);
                if (!nearVerticalPageEdge(line)) continue;
                removed.addAll(line.elementIds());
            }
        }
        return removed;
    }

    private Set<String> configuredWatermarkElements(PdfLoader.PdfDocument document,
                                                    KnowledgeProperties.PdfPreprocessing config) {
        Set<String> configured = new HashSet<>();
        if (config.getWatermarkLines() == null || config.getWatermarkLines().isEmpty()) return configured;
        Set<String> watermarks = new HashSet<>();
        for (String value : config.getWatermarkLines()) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) watermarks.add(normalized);
        }
        for (PdfLoader.TextElement element : document.elements()) {
            if (watermarks.contains(normalize(element.text()))) configured.add(element.id());
        }
        return configured;
    }

    private PdfCleanupResult pages(PdfLoader.PdfDocument document, Set<String> removed, List<String> warnings,
                                   KnowledgeProperties.PdfPreprocessing config) {
        return pages(document, removed, warnings, config, Set.of());
    }

    private PdfCleanupResult pages(PdfLoader.PdfDocument document, Set<String> removed, List<String> warnings,
                                   KnowledgeProperties.PdfPreprocessing config,
                                   Set<Integer> tableOfContentsPages) {
        List<Page> pages = new ArrayList<>();
        int removedCount = 0;
        for (PdfLoader.PdfPage page : document.pages()) {
            StringBuilder pageText = new StringBuilder();
            TextLine previousLine = null;
            for (TextLine line : lines(page.elements())) {
                List<PdfLoader.TextElement> remaining = new ArrayList<>();
                for (PdfLoader.TextElement element : line.elements()) {
                    if (removed.contains(element.id())) {
                        removedCount++;
                    } else {
                        remaining.add(element);
                    }
                }
                TextLine cleanedLine = new TextLine(remaining);
                String text = normalize(cleanedLine.text());
                if (text.isBlank()) continue;
                if (!pageText.isEmpty()) {
                    pageText.append(startsNewParagraph(previousLine, cleanedLine, config) ? "\n\n" : "\n");
                }
                pageText.append(text);
                previousLine = cleanedLine;
            }
            pages.add(new Page(page.number(), "", normalize(pageText.toString())));
        }
        List<String> resultWarnings = removedCount == 0 ? List.of() : warnings;
        return new PdfCleanupResult(pages, resultWarnings, removedCount,
                tableOfContentsPages.stream().sorted().toList());
    }

    private List<PdfLoader.TextElement> sorted(List<PdfLoader.TextElement> elements) {
        return elements.stream().sorted(Comparator.comparing(PdfLoader.TextElement::y)
                .thenComparing(PdfLoader.TextElement::x)
                .thenComparingInt(PdfLoader.TextElement::lineIndex)).toList();
    }

    private List<TextLine> lines(List<PdfLoader.TextElement> elements) {
        Map<Integer, List<PdfLoader.TextElement>> byLineIndex = new LinkedHashMap<>();
        for (PdfLoader.TextElement element : sorted(elements)) {
            byLineIndex.computeIfAbsent(element.lineIndex(), ignored -> new ArrayList<>()).add(element);
        }
        return byLineIndex.values().stream().map(TextLine::new)
                .sorted(Comparator.comparing(TextLine::y).thenComparing(TextLine::x))
                .toList();
    }

    private boolean startsNewParagraph(TextLine previousLine, TextLine line,
                                       KnowledgeProperties.PdfPreprocessing config) {
        if (previousLine == null || isStandaloneListMarker(normalize(previousLine.text()), config)) {
            return false;
        }
        float lineHeight = Math.max(1F, Math.max(previousLine.height(), line.height()));
        float verticalGap = line.y() - previousLine.y() - previousLine.height();
        if (verticalGap > lineHeight * 2F) return true;
        float indent = line.x() - previousLine.x();
        return indent > lineHeight * 1.5F && endsParagraph(normalize(previousLine.text()));
    }

    private boolean endsParagraph(String text) {
        return text.matches(".*[。！？；;.!?]$");
    }

    private boolean nearVerticalPageEdge(TextLine line) {
        if (line.pageHeight() <= 0) return true;
        float centerY = line.y() + line.height() / 2F;
        float edgeBand = line.pageHeight() * .25F;
        return centerY <= edgeBand || centerY >= line.pageHeight() - edgeBand;
    }

    private boolean isStandaloneListMarker(String text, KnowledgeProperties.PdfPreprocessing config) {
        return matchesAny(text, config.getStandaloneListMarkerPatterns());
    }

    private boolean matchesAny(String text, List<String> patterns) {
        if (text == null || text.isBlank() || patterns == null || patterns.isEmpty()) return false;
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && Pattern.compile(pattern).matcher(text).matches()) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+", " ").trim();
    }

    public record PdfCleanupResult(List<Page> pages, List<String> warnings, int removedCount,
                                   List<Integer> tableOfContentsPages) {
        public PdfCleanupResult(List<Page> pages, List<String> warnings, int removedCount) {
            this(pages, warnings, removedCount, List.of());
        }
    }

    private record TableOfContentsDetection(Set<String> elements, Set<Integer> pages) {
    }

    private record TextLine(List<PdfLoader.TextElement> elements) {
        private String text() {
            return elements.stream()
                    .sorted(Comparator.comparing(PdfLoader.TextElement::x)
                            .thenComparingInt(PdfLoader.TextElement::lineIndex))
                    .map(PdfLoader.TextElement::text)
                    .reduce("", String::concat);
        }

        private List<String> elementIds() {
            return elements.stream().map(PdfLoader.TextElement::id).toList();
        }

        private float x() {
            return elements.stream().map(PdfLoader.TextElement::x).min(Float::compareTo).orElse(0F);
        }

        private float y() {
            return elements.stream().map(PdfLoader.TextElement::y).min(Float::compareTo).orElse(0F);
        }

        private float height() {
            return elements.stream().map(PdfLoader.TextElement::height).max(Float::compareTo).orElse(0F);
        }

        private float pageHeight() {
            return elements.stream().findFirst().map(PdfLoader.TextElement::pageHeight).orElse(0F);
        }
    }
}
