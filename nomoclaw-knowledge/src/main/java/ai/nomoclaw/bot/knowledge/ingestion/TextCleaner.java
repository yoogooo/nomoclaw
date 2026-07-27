package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser.Page;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser.PdfPreprocessingOptions;
import ai.nomoclaw.bot.knowledge.ingestion.DocumentParser.PreprocessingOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            return pages(document, Set.of(), List.of());
        }
        PdfPreprocessingOptions pdf = preprocessing.pdfOptions();
        if (!pdf.removeHeader() && !pdf.removeFooter() && !pdf.removeWatermark()) {
            return pages(document, Set.of(), List.of());
        }
        Set<String> removed = new HashSet<>();
        if (pdf.removeHeader()) {
            removed.addAll(repeatedMarginElements(document, true, config));
        }
        if (pdf.removeFooter()) {
            removed.addAll(repeatedMarginElements(document, false, config));
        }
        if (pdf.removeWatermark()) {
            removed.addAll(watermarkDetector.detect(document, config));
            removed.addAll(configuredWatermarkElements(document, config));
        }
        List<String> warnings = removed.isEmpty() ? List.of() : List.of("PDF_PREPROCESSING_APPLIED");
        return pages(document, removed, warnings);
    }

    private Set<String> repeatedMarginElements(PdfLoader.PdfDocument document, boolean header,
                                               KnowledgeProperties.PdfPreprocessing config) {
        if (document.pages().size() < 2) return Set.of();
        int maxLines = Math.max(0, header ? config.getMaxHeaderLines() : config.getMaxFooterLines());
        if (maxLines == 0) return Set.of();
        Map<String, Set<Integer>> pagesByText = new HashMap<>();
        Map<String, List<String>> idsByText = new HashMap<>();
        for (PdfLoader.PdfPage page : document.pages()) {
            List<TextLine> lines = lines(page.elements());
            int size = lines.size();
            for (int offset = 0; offset < Math.min(maxLines, size); offset++) {
                TextLine line = header ? lines.get(offset) : lines.get(size - 1 - offset);
                if (!nearVerticalPageEdge(line)) continue;
                String text = normalize(line.text());
                if (text.isBlank()) continue;
                pagesByText.computeIfAbsent(text, ignored -> new HashSet<>()).add(page.number());
                idsByText.computeIfAbsent(text, ignored -> new ArrayList<>()).addAll(line.elementIds());
            }
        }
        int threshold = Math.max(2, (int) Math.ceil(document.pages().size()
                * Math.max(0, config.getRepeatedLineThresholdRatio())));
        Set<String> removed = new HashSet<>();
        pagesByText.forEach((text, pages) -> {
            if (pages.size() >= threshold) removed.addAll(idsByText.getOrDefault(text, List.of()));
        });
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

    private PdfCleanupResult pages(PdfLoader.PdfDocument document, Set<String> removed, List<String> warnings) {
        List<Page> pages = new ArrayList<>();
        int removedCount = 0;
        for (PdfLoader.PdfPage page : document.pages()) {
            List<String> pageLines = new ArrayList<>();
            for (TextLine line : lines(page.elements())) {
                List<PdfLoader.TextElement> remaining = new ArrayList<>();
                for (PdfLoader.TextElement element : line.elements()) {
                    if (removed.contains(element.id())) {
                        removedCount++;
                    } else {
                        remaining.add(element);
                    }
                }
                String text = normalize(joinLine(remaining));
                if (!text.isBlank()) pageLines.add(text);
            }
            pages.add(new Page(page.number(), "", normalize(String.join("\n", pageLines))));
        }
        List<String> resultWarnings = removedCount == 0 ? List.of() : warnings;
        return new PdfCleanupResult(pages, resultWarnings, removedCount);
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

    private String joinLine(List<PdfLoader.TextElement> elements) {
        return elements.stream()
                .sorted(Comparator.comparing(PdfLoader.TextElement::x)
                        .thenComparingInt(PdfLoader.TextElement::lineIndex))
                .map(PdfLoader.TextElement::text)
                .reduce("", String::concat);
    }

    private boolean nearVerticalPageEdge(TextLine line) {
        if (line.pageHeight() <= 0) return true;
        float centerY = line.y() + line.height() / 2F;
        float edgeBand = line.pageHeight() * .25F;
        return centerY <= edgeBand || centerY >= line.pageHeight() - edgeBand;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+", " ").trim();
    }

    public record PdfCleanupResult(List<Page> pages, List<String> warnings, int removedCount) {
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
