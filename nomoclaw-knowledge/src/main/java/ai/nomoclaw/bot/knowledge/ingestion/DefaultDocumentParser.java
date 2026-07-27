package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Parser for PDF, DOCX, Markdown, and UTF-8 text documents.
 */
@Component
public class DefaultDocumentParser implements DocumentParser {
    private final KnowledgeProperties properties;

    public DefaultDocumentParser() {
        this(new KnowledgeProperties());
    }

    public DefaultDocumentParser(KnowledgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(String contentType, String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt") || name.endsWith(".md");
    }

    @Override
    public ParsedDocument parse(Path file) {
        return parse(file, (processed, total) -> {
        });
    }

    @Override
    public ParsedDocument parse(Path file, PageProgress progress) {
        return parse(file, PreprocessingOptions.disabled(), progress);
    }

    @Override
    public ParsedDocument parse(Path file, PreprocessingOptions preprocessing, PageProgress progress) {
        try {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".pdf")) return parsePdf(file, preprocessing, progress);
            ParsedDocument parsed = name.endsWith(".docx") ? parseDocx(file) : parseText(file);
            progress.accept(1, 1);
            return parsed;
        } catch (KnowledgeParseException ex) {
            throw ex;
        } catch (ParsingAbortedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new KnowledgeParseException("PARSE_FAILED", "文档解析失败: " + ex.getMessage(), ex);
        }
    }

    private ParsedDocument parsePdf(Path file, PreprocessingOptions preprocessing, PageProgress progress) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            if (document.isEncrypted()) throw new KnowledgeParseException("ENCRYPTED_PDF", "不支持加密 PDF");
            PDFTextStripper stripper = new PDFTextStripper();
            List<Page> pages = new ArrayList<>();
            List<DocumentBlock> blocks = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int characters = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                characters += text.length();
                pages.add(new Page(page, "", text));
                progress.accept(page, document.getNumberOfPages());
            }
            if (characters < 20)
                throw new KnowledgeParseException("OCR_REQUIRED", "PDF 未提取到足够文本，可能是扫描件，需要 OCR");
            PdfCleanupResult cleanup = cleanupPdfPages(pages, preprocessing);
            List<Page> cleanedPages = cleanup.pages();
            warnings.addAll(cleanup.warnings());
            int emptyPages = 0;
            for (Page page : cleanedPages) {
                if (page.text().isBlank()) {
                    emptyPages++;
                    continue;
                }
                for (String paragraph : page.text().split("\\n\\s*\\n")) {
                    String value = normalize(paragraph);
                    if (!value.isBlank()) {
                        blocks.add(new DocumentBlock(BlockType.PARAGRAPH, page.number(), "", value));
                    }
                }
            }
            if (emptyPages > 0) warnings.add("PARTIAL_TEXT_EXTRACTION");
            return new ParsedDocument(cleanedPages, blocks, warnings);
        }
    }

    private ParsedDocument parseDocx(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file); XWPFDocument document = new XWPFDocument(input)) {
            List<Page> pages = new ArrayList<>();
            List<DocumentBlock> blocks = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();
            Map<Integer, String> headings = new LinkedHashMap<>();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String value = normalize(paragraph.getText());
                    if (value.isBlank()) continue;
                    int headingLevel = headingLevel(paragraph.getStyle());
                    if (headingLevel > 0) {
                        headings.entrySet().removeIf(entry -> entry.getKey() >= headingLevel);
                        headings.put(headingLevel, value);
                        blocks.add(new DocumentBlock(BlockType.HEADING, 1, sectionPath(headings), value));
                    } else {
                        BlockType type = paragraph.getNumID() == null ? BlockType.PARAGRAPH : BlockType.LIST;
                        blocks.add(new DocumentBlock(type, 1, sectionPath(headings), value));
                    }
                    fullText.append(value).append("\n\n");
                } else if (element instanceof XWPFTable table) {
                    String value = tableText(table);
                    if (!value.isBlank()) {
                        blocks.add(new DocumentBlock(BlockType.TABLE, 1, sectionPath(headings), value));
                        fullText.append(value).append("\n\n");
                    }
                }
            }
            if (fullText.isEmpty()) throw new KnowledgeParseException("EMPTY_DOCUMENT", "文档没有可索引文本");
            pages.add(new Page(1, sectionPath(headings), fullText.toString().trim()));
            return new ParsedDocument(pages, blocks, List.of());
        }
    }

    private ParsedDocument parseText(Path file) throws Exception {
        String text;
        try (Reader reader = new InputStreamReader(Files.newInputStream(file),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT))) {
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) value.append(buffer, 0, read);
            text = value.toString();
        } catch (CharacterCodingException ex) {
            throw new KnowledgeParseException("INVALID_ENCODING", "文本文件必须使用 UTF-8 编码", ex);
        }
        text = normalize(text.replace("\uFEFF", ""));
        if (text.isBlank()) throw new KnowledgeParseException("EMPTY_DOCUMENT", "文档没有可索引文本");
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md")) {
            return parseMarkdown(text);
        }
        List<DocumentBlock> blocks = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String value = normalize(paragraph);
            if (!value.isBlank()) blocks.add(new DocumentBlock(BlockType.PARAGRAPH, 1, "", value));
        }
        return new ParsedDocument(List.of(new Page(1, "", text)), blocks, List.of());
    }

    private ParsedDocument parseMarkdown(String text) {
        List<DocumentBlock> blocks = new ArrayList<>();
        Map<Integer, String> headings = new LinkedHashMap<>();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder fenced = new StringBuilder();
        boolean inFence = false;
        for (String line : text.split("\\n", -1)) {
            if (line.trim().startsWith("```")) {
                flushParagraph(blocks, paragraph, headings);
                if (inFence) {
                    blocks.add(new DocumentBlock(BlockType.CODE, 1, sectionPath(headings), fenced.toString().trim()));
                    fenced.setLength(0);
                }
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                fenced.append(line).append('\n');
                continue;
            }
            if (line.matches("^#{1,6}\\s+.*")) {
                flushParagraph(blocks, paragraph, headings);
                int level = line.indexOf(' ');
                String value = normalize(line.substring(level + 1));
                headings.entrySet().removeIf(entry -> entry.getKey() >= level);
                headings.put(level, value);
                blocks.add(new DocumentBlock(BlockType.HEADING, 1, sectionPath(headings), value));
            } else if (line.matches("^\\s*([-*+] |\\d+[.)] ).*")) {
                flushParagraph(blocks, paragraph, headings);
                blocks.add(new DocumentBlock(BlockType.LIST, 1, sectionPath(headings), normalize(line)));
            } else if (line.contains("|") && !line.isBlank()) {
                flushParagraph(blocks, paragraph, headings);
                blocks.add(new DocumentBlock(BlockType.TABLE, 1, sectionPath(headings), normalize(line)));
            } else if (line.isBlank()) {
                flushParagraph(blocks, paragraph, headings);
            } else {
                paragraph.append(line).append('\n');
            }
        }
        flushParagraph(blocks, paragraph, headings);
        if (inFence && !fenced.isEmpty()) {
            blocks.add(new DocumentBlock(BlockType.CODE, 1, sectionPath(headings), fenced.toString().trim()));
        }
        return new ParsedDocument(List.of(new Page(1, sectionPath(headings), text)), blocks, List.of());
    }

    private PdfCleanupResult cleanupPdfPages(List<Page> pages, PreprocessingOptions preprocessing) {
        if (preprocessing == null || !preprocessing.enabled()) {
            return new PdfCleanupResult(pages, List.of());
        }
        PdfPreprocessingOptions pdf = preprocessing.pdfOptions();
        if (!pdf.removeHeader() && !pdf.removeFooter() && !pdf.removeWatermark()) {
            return new PdfCleanupResult(pages, List.of());
        }
        KnowledgeProperties.PdfPreprocessing config = properties.getParsing().getPdf().getPreprocessing();
        Set<String> headers = normalizedSet(config.getHeaderLines());
        Set<String> footers = normalizedSet(config.getFooterLines());
        Set<String> watermarks = normalizedSet(config.getWatermarkLines());
        Set<String> repeatedHeaders = pdf.removeHeader() ? repeatedMarginLines(pages, true, config) : Set.of();
        Set<String> repeatedFooters = pdf.removeFooter() ? repeatedMarginLines(pages, false, config) : Set.of();
        List<String> warnings = new ArrayList<>();
        int removed = 0;
        List<Page> cleaned = new ArrayList<>(pages.size());
        for (Page page : pages) {
            List<String> lines = new ArrayList<>(page.text().lines().toList());
            int before = lines.size();
            if (pdf.removeWatermark() && !watermarks.isEmpty()) {
                lines.removeIf(line -> watermarks.contains(normalize(line)));
            }
            if (pdf.removeHeader()) {
                removeConfiguredMargin(lines, headers, true, config.getMaxHeaderLines());
                removeRepeatedMargin(lines, repeatedHeaders, true, config.getMaxHeaderLines());
            }
            if (pdf.removeFooter()) {
                removeConfiguredMargin(lines, footers, false, config.getMaxFooterLines());
                removeRepeatedMargin(lines, repeatedFooters, false, config.getMaxFooterLines());
            }
            removed += before - lines.size();
            cleaned.add(new Page(page.number(), page.section(), normalize(String.join("\n", lines))));
        }
        if (removed > 0) warnings.add("PDF_PREPROCESSING_APPLIED");
        return new PdfCleanupResult(cleaned, warnings);
    }

    private Set<String> repeatedMarginLines(List<Page> pages, boolean header, KnowledgeProperties.PdfPreprocessing config) {
        if (pages.size() < 2) return Set.of();
        int maxLines = Math.max(0, header ? config.getMaxHeaderLines() : config.getMaxFooterLines());
        if (maxLines == 0) return Set.of();
        Map<String, Integer> firstLines = new HashMap<>();
        for (Page page : pages) {
            List<String> lines = page.text().lines().map(String::trim).filter(line -> !line.isBlank()).toList();
            int limit = Math.min(maxLines, lines.size());
            for (int i = 0; i < limit; i++) {
                String value = header ? lines.get(i) : lines.get(lines.size() - 1 - i);
                firstLines.merge(normalize(value), 1, Integer::sum);
            }
        }
        int threshold = Math.max(2, (int) Math.ceil(pages.size() * Math.max(0, config.getRepeatedLineThresholdRatio())));
        Set<String> repeated = new HashSet<>();
        firstLines.forEach((line, count) -> {
            if (!line.isBlank() && count >= threshold) repeated.add(line);
        });
        return repeated;
    }

    private void removeConfiguredMargin(List<String> lines, Set<String> values, boolean header, int maxLines) {
        if (values.isEmpty() || maxLines <= 0) return;
        removeMargin(lines, line -> values.contains(normalize(line)), header, maxLines);
    }

    private void removeRepeatedMargin(List<String> lines, Set<String> values, boolean header, int maxLines) {
        if (values.isEmpty() || maxLines <= 0) return;
        removeMargin(lines, line -> values.contains(normalize(line)), header, maxLines);
    }

    private void removeMargin(List<String> lines, Predicate<String> predicate, boolean header, int maxLines) {
        int inspected = 0;
        while (!lines.isEmpty() && inspected < maxLines) {
            int index = header ? 0 : lines.size() - 1;
            if (!predicate.test(lines.get(index))) break;
            lines.remove(index);
            inspected++;
        }
    }

    private Set<String> normalizedSet(List<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) return result;
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return result;
    }

    private int headingLevel(String style) {
        if (style == null) return 0;
        String normalized = style.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("heading")) return 0;
        String digits = normalized.replaceAll("\\D+", "");
        return digits.isBlank() ? 1 : Math.max(1, Math.min(6, Integer.parseInt(digits)));
    }

    private String tableText(XWPFTable table) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            rows.add(row.getTableCells().stream().map(XWPFTableCell::getText)
                    .map(this::normalize).toList().stream().reduce((left, right) -> left + " | " + right).orElse(""));
        }
        return String.join("\n", rows);
    }

    private String sectionPath(Map<Integer, String> headings) {
        return String.join(" / ", headings.values());
    }

    private void flushParagraph(List<DocumentBlock> blocks, StringBuilder paragraph,
                                Map<Integer, String> headings) {
        String value = normalize(paragraph.toString());
        if (!value.isBlank()) {
            blocks.add(new DocumentBlock(BlockType.PARAGRAPH, 1, sectionPath(headings), value));
        }
        paragraph.setLength(0);
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[ \\t]+", " ").trim();
    }

    private record PdfCleanupResult(List<Page> pages, List<String> warnings) {
    }

    /**
     * Stable parser error exposed by ingestion jobs.
     */
    public static class KnowledgeParseException extends RuntimeException {
        private final String code;

        public KnowledgeParseException(String code, String message) {
            super(message);
            this.code = code;
        }

        public KnowledgeParseException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
