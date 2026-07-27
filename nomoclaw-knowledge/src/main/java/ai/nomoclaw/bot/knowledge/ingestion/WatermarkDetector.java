package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects repeated text elements that look like PDF text watermarks.
 */
@Component
public class WatermarkDetector {

    public Set<String> detect(PdfLoader.PdfDocument document, KnowledgeProperties.PdfPreprocessing config) {
        Set<String> ids = new LinkedHashSet<>();
        detectWatermarks(document, config).forEach(watermark -> ids.addAll(watermark.elementIds()));
        return ids;
    }

    public List<DetectedWatermark> detectWatermarks(PdfLoader.PdfDocument document,
                                                    KnowledgeProperties.PdfPreprocessing config) {
        if (document.pages().size() < 2) return List.of();
        boolean normalizeSpacing = config.isWatermarkNormalizeSpacing();
        Map<String, CandidateStats> stats = new HashMap<>();
        for (PdfLoader.TextElement element : document.elements()) {
            String text = element.normalizedText(normalizeSpacing);
            String key = candidateKey(text, element, config);
            if (key == null) continue;
            stats.computeIfAbsent(key, ignored -> new CandidateStats()).add(element, config);
        }
        int threshold = Math.max(2, (int) Math.ceil(document.pages().size()
                * Math.max(0, config.getWatermarkRepeatedThresholdRatio())));
        List<DetectedWatermark> lowLevelWatermarks = new ArrayList<>();
        stats.forEach((text, value) -> {
            if (value.pages.size() >= threshold && value.layoutScore > 0) {
                lowLevelWatermarks.add(value.toDetectedWatermark(text, document.pages().size()));
            }
        });
        return semanticWatermarks(document, lowLevelWatermarks);
    }

    private String candidateKey(String text, PdfLoader.TextElement element,
                                KnowledgeProperties.PdfPreprocessing config) {
        int length = text == null ? 0 : text.length();
        if (length == 1) {
            return text + "@" + coordinateBucket(element.x() + element.width() / 2F)
                    + ":" + coordinateBucket(element.y() + element.height() / 2F);
        }
        if (length >= Math.max(1, config.getWatermarkMinTextLength())
                && length <= Math.max(config.getWatermarkMinTextLength(), config.getWatermarkMaxTextLength())) {
            return text;
        }
        return null;
    }

    private int coordinateBucket(float value) {
        return Math.round(value / 12F);
    }

    public record DetectedWatermark(String text, List<String> elementIds, int firstPage, int pageCount,
                                    double confidence) {
    }

    private List<DetectedWatermark> semanticWatermarks(PdfLoader.PdfDocument document,
                                                       List<DetectedWatermark> lowLevelWatermarks) {
        if (lowLevelWatermarks.isEmpty()) {
            return lowLevelWatermarks;
        }
        Set<String> elementIds = new LinkedHashSet<>();
        Set<Integer> pages = new HashSet<>();
        double confidence = 0D;
        List<String> phrases = new ArrayList<>();
        for (DetectedWatermark watermark : lowLevelWatermarks) {
            elementIds.addAll(watermark.elementIds());
            pages.addAll(pagesByElementIds(document, watermark.elementIds()));
            confidence = Math.max(confidence, watermark.confidence());
            if (watermark.text().length() > 1 && phrases.stream().noneMatch(watermark.text()::contains)) {
                phrases.add(watermark.text());
            }
        }
        List<String> reconstructed = semanticWatermarkPhrases(diagonalLines(document, elementIds));
        for (String phrase : reconstructed) {
            if (phrases.stream().noneMatch(value -> value.contains(phrase) || phrase.contains(value))) {
                phrases.add(phrase);
            }
        }
        if (phrases.isEmpty()) {
            return lowLevelWatermarks;
        }
        String text = String.join("\n", phrases);
        int firstPage = pages.stream().min(Integer::compareTo).orElse(1);
        return List.of(new DetectedWatermark(text, List.copyOf(elementIds), firstPage, pages.size(), confidence));
    }

    private Set<Integer> pagesByElementIds(PdfLoader.PdfDocument document, List<String> elementIds) {
        Set<String> ids = new HashSet<>(elementIds);
        return document.elements().stream()
                .filter(element -> ids.contains(element.id()))
                .map(PdfLoader.TextElement::page)
                .collect(Collectors.toSet());
    }

    private List<String> diagonalLines(PdfLoader.PdfDocument document, Set<String> watermarkElementIds) {
        int firstPage = document.elements().stream()
                .filter(element -> watermarkElementIds.contains(element.id()))
                .map(PdfLoader.TextElement::page)
                .min(Integer::compareTo)
                .orElse(1);
        List<PdfLoader.TextElement> elements = document.elements().stream()
                .filter(element -> element.page() == firstPage)
                .filter(element -> watermarkElementIds.contains(element.id()))
                .toList();
        List<String> lines = new ArrayList<>();
        for (float bucketSize : List.of(8F, 12F, 16F, 24F)) {
            Map<Integer, List<PdfLoader.TextElement>> diagonalLines = elements.stream()
                    .collect(Collectors.groupingBy(element -> diagonalBucket(element, bucketSize),
                            LinkedHashMap::new, Collectors.toList()));
            lines.addAll(diagonalLines.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .map(this::joinDiagonalLine)
                    .filter(line -> line.length() >= 4)
                    .toList());
        }
        return lines.stream().distinct()
                .toList();
    }

    private int diagonalBucket(PdfLoader.TextElement element, float bucketSize) {
        return Math.round((element.x() + element.y()) / bucketSize);
    }

    private String joinDiagonalLine(List<PdfLoader.TextElement> elements) {
        return elements.stream()
                .sorted(Comparator.comparing(PdfLoader.TextElement::x))
                .map(PdfLoader.TextElement::text)
                .reduce("", String::concat)
                .trim();
    }

    private List<String> semanticWatermarkPhrases(List<String> lines) {
        List<String> prefixPhrases = prefixWatermarkPhrases(lines);
        if (!prefixPhrases.isEmpty()) {
            return prefixPhrases;
        }
        return repeatedWatermarkPhrases(lines);
    }

    private List<String> prefixWatermarkPhrases(List<String> lines) {
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines) {
            int maxLength = Math.min(30, line.length() - 1);
            for (int length = 4; length <= maxLength; length++) {
                String phrase = line.substring(0, length);
                counts.merge(phrase, 1, Integer::sum);
            }
        }
        Set<String> frequentPhrases = counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        List<String> phrases = new ArrayList<>();
        counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .sorted(Comparator.<Map.Entry<String, Integer>, Integer>comparing(entry -> entry.getKey().length())
                        .reversed()
                        .thenComparing(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> {
                    String phrase = entry.getKey();
                    if (!isRepeatedExtension(phrase, frequentPhrases)
                            && phrases.stream().noneMatch(selected -> selected.contains(phrase))) {
                        phrases.add(phrase);
                    }
                });
        return phrases.stream().limit(8).toList();
    }

    private List<String> repeatedWatermarkPhrases(List<String> lines) {
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines) {
            int maxLength = Math.min(30, line.length());
            for (int length = 4; length <= maxLength; length++) {
                for (int start = 0; start + length <= line.length(); start++) {
                    String phrase = line.substring(start, start + length);
                    counts.merge(phrase, 1, Integer::sum);
                }
            }
        }
        List<String> phrases = new ArrayList<>();
        counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .sorted(Comparator.<Map.Entry<String, Integer>, Integer>comparing(entry -> entry.getKey().length())
                        .reversed()
                        .thenComparing(Map.Entry.<String, Integer>comparingByValue().reversed())
                .thenComparing(Map.Entry::getKey))
                .forEach(entry -> {
                    String phrase = collapseRepeatedPhrase(entry.getKey());
                    if (phrases.stream().noneMatch(selected -> selected.contains(phrase))) {
                        phrases.add(phrase);
                    }
                });
        return phrases.stream().limit(8).toList();
    }

    private String collapseRepeatedPhrase(String phrase) {
        for (int length = phrase.length() / 2; length >= 4; length--) {
            String prefix = phrase.substring(0, length);
            String suffix = phrase.substring(length);
            if (suffix.startsWith(prefix.substring(0, Math.min(prefix.length(), suffix.length())))) {
                return prefix;
            }
        }
        return phrase;
    }

    private boolean isRepeatedExtension(String phrase, Set<String> frequentPhrases) {
        return frequentPhrases.stream()
                .filter(candidate -> candidate.length() >= 4 && candidate.length() < phrase.length())
                .anyMatch(candidate -> startsWithRepeatedPrefix(phrase, candidate));
    }

    private boolean startsWithRepeatedPrefix(String phrase, String candidate) {
        if (!phrase.startsWith(candidate)) {
            return false;
        }
        String remainder = phrase.substring(candidate.length());
        int overlapLength = Math.min(Math.min(3, candidate.length()), remainder.length());
        return overlapLength > 0 && remainder.startsWith(candidate.substring(0, overlapLength));
    }

    private static final class CandidateStats {
        private final Set<Integer> pages = new HashSet<>();
        private final List<PdfLoader.TextElement> elements = new ArrayList<>();
        private final List<String> elementIds = new ArrayList<>();
        private int layoutScore;

        private void add(PdfLoader.TextElement element, KnowledgeProperties.PdfPreprocessing config) {
            pages.add(element.page());
            elements.add(element);
            elementIds.add(element.id());
            if (isLikelyWatermarkLayout(element, config)) layoutScore++;
        }

        private DetectedWatermark toDetectedWatermark(String fallbackText, int totalPages) {
            List<PdfLoader.TextElement> firstPageElements = elements.stream()
                    .filter(element -> element.page() == pages.stream().min(Integer::compareTo).orElse(1))
                    .sorted(Comparator.comparing(PdfLoader.TextElement::y)
                            .thenComparing(PdfLoader.TextElement::x)
                            .thenComparingInt(PdfLoader.TextElement::lineIndex))
                    .toList();
            String text = rebuildText(firstPageElements);
            double confidence = Math.min(1D, (double) pages.size() / Math.max(1, totalPages));
            return new DetectedWatermark(text.isBlank() ? fallbackText : text, List.copyOf(elementIds),
                    pages.stream().min(Integer::compareTo).orElse(1), pages.size(), confidence);
        }

        private String rebuildText(List<PdfLoader.TextElement> values) {
            if (values.isEmpty()) return "";
            List<String> lines = new ArrayList<>();
            List<PdfLoader.TextElement> currentLine = new ArrayList<>();
            float currentY = Float.NaN;
            for (PdfLoader.TextElement element : values) {
                if (Float.isNaN(currentY) || Math.abs(element.y() - currentY) <= Math.max(4F, element.height())) {
                    currentLine.add(element);
                    currentY = Float.isNaN(currentY) ? element.y() : Math.min(currentY, element.y());
                } else {
                    lines.add(joinLine(currentLine));
                    currentLine.clear();
                    currentLine.add(element);
                    currentY = element.y();
                }
            }
            if (!currentLine.isEmpty()) lines.add(joinLine(currentLine));
            return String.join("\n", lines).trim();
        }

        private String joinLine(List<PdfLoader.TextElement> values) {
            return values.stream()
                    .sorted(Comparator.comparing(PdfLoader.TextElement::x)
                            .thenComparingInt(PdfLoader.TextElement::lineIndex))
                    .map(PdfLoader.TextElement::text)
                    .reduce("", String::concat)
                    .trim();
        }

        private boolean isLikelyWatermarkLayout(PdfLoader.TextElement element,
                                                KnowledgeProperties.PdfPreprocessing config) {
            return WatermarkDetector.isLikelyWatermarkLayout(element, config);
        }
    }

    private static boolean isLikelyWatermarkLayout(PdfLoader.TextElement element,
                                                   KnowledgeProperties.PdfPreprocessing config) {
        float centerRatio = Math.max(0F, Math.min(1F, (float) config.getWatermarkCenterRegionRatio()));
        float horizontalMargin = element.pageWidth() * (1F - centerRatio) / 2F;
        float verticalMargin = element.pageHeight() * (1F - centerRatio) / 2F;
        float centerX = element.x() + element.width() / 2F;
        float centerY = element.y() + element.height() / 2F;
        boolean inCenter = centerX >= horizontalMargin && centerX <= element.pageWidth() - horizontalMargin
                && centerY >= verticalMargin && centerY <= element.pageHeight() - verticalMargin;
        boolean rotated = Math.abs(element.rotation()) >= config.getWatermarkRotationThresholdDegrees()
                && Math.abs(element.rotation() - 360F) >= config.getWatermarkRotationThresholdDegrees();
        boolean large = element.fontSize() >= config.getWatermarkMinFontSize();
        return inCenter || rotated || large;
    }
}
