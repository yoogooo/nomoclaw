package ai.nomoclaw.bot.knowledge.ingestion;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parser for PDF, DOCX, Markdown, and UTF-8 text documents.
 */
@Component
public class DefaultDocumentParser implements DocumentParser {
    private static final Pattern PDF_LIST_MARKER = Pattern.compile(
            "^(?:\\d+|[一二三四五六七八九十]+)[.)．、]|^[（(][一二三四五六七八九十\\d]+[）)]");

    private final KnowledgeProperties properties;
    private final PdfLoader pdfLoader;
    private final TextCleaner textCleaner;

    public DefaultDocumentParser() {
        this(new KnowledgeProperties());
    }

    public DefaultDocumentParser(KnowledgeProperties properties) {
        this(properties, new PdfLoader(), new TextCleaner(new WatermarkDetector()));
    }

    @Autowired
    public DefaultDocumentParser(KnowledgeProperties properties, PdfLoader pdfLoader, TextCleaner textCleaner) {
        this.properties = properties;
        this.pdfLoader = pdfLoader;
        this.textCleaner = textCleaner;
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
            PdfLoader.PdfDocument pdf = pdfLoader.load(document, progress);
            List<DocumentBlock> blocks = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int characters = pdf.elements().stream().mapToInt(element -> normalize(element.text()).length()).sum();
            if (characters < 20)
                throw new KnowledgeParseException("OCR_REQUIRED", "PDF 未提取到足够文本，可能是扫描件，需要 OCR");
            TextCleaner.PdfCleanupResult cleanup = textCleaner.clean(pdf, preprocessing,
                    properties.getParsing().getPdf().getPreprocessing());
            List<Page> cleanedPages = cleanup.pages();
            warnings.addAll(cleanup.warnings());
            Map<String, OutlineHeading> outlineHeadings = pdfOutline(document);
            int emptyPages = 0;
            for (Page page : cleanedPages) {
                if (page.text().isBlank()) {
                    emptyPages++;
                    continue;
                }
                for (String paragraph : page.text().split("\\n\\s*\\n")) {
                    String value = normalizePdfParagraph(paragraph);
                    for (String logicalParagraph : value.split("\\n\\s*\\n")) {
                        String normalized = normalize(logicalParagraph);
                        if (!normalized.isBlank()) {
                            float fontSize = pdfFontSize(pdf, page.number(), normalized);
                            OutlineHeading outline = outlineHeadings.get(outlineKey(page.number(), normalized));
                            BlockType type = outline != null || isPdfHeading(normalized, fontSize, medianPdfFontSize(pdf))
                                    ? BlockType.HEADING : BlockType.PARAGRAPH;
                            BlockStyle style = outline == null
                                    ? new BlockStyle(fontSize, false, 0F, 0F, 0F)
                                    : new BlockStyle(outline.level(), true, 0F, 0F, 0F);
                            blocks.add(new DocumentBlock(type, page.number(), page.number(), "", "root", normalized,
                                    0, normalized.length(), style, false));
                        }
                    }
                }
            }
            if (emptyPages > 0) warnings.add("PARTIAL_TEXT_EXTRACTION");
            ParsedDocument parsed = structured(cleanedPages, blocks, warnings,
                    outlineHeadings.isEmpty() ? "LAYOUT" : "PDF_OUTLINE");
            return addFilteredTableOfContentsNodes(parsed, cleanup.tableOfContentsPages());
        }
    }

    private Map<String, OutlineHeading> pdfOutline(PDDocument document) throws Exception {
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline == null) return Map.of();
        Map<String, OutlineHeading> headings = new LinkedHashMap<>();
        collectOutline(document, outline.getFirstChild(), 1, headings);
        return headings;
    }

    private void collectOutline(PDDocument document, PDOutlineItem item, int level,
                                Map<String, OutlineHeading> headings) throws Exception {
        PDOutlineItem current = item;
        while (current != null) {
            PDPage destination = current.findDestinationPage(document);
            int page = destination == null ? 0 : document.getPages().indexOf(destination) + 1;
            String title = normalize(current.getTitle());
            if (page > 0 && !title.isBlank()) {
                headings.putIfAbsent(outlineKey(page, title), new OutlineHeading(level));
            }
            collectOutline(document, current.getFirstChild(), level + 1, headings);
            current = current.getNextSibling();
        }
    }

    private String outlineKey(int page, String title) {
        return page + "|" + normalize(title).toLowerCase(Locale.ROOT);
    }

    private ParsedDocument addFilteredTableOfContentsNodes(ParsedDocument parsed, List<Integer> pageNumbers) {
        if (pageNumbers.isEmpty()) return parsed;
        List<StructureNode> nodes = new ArrayList<>(parsed.nodes());
        for (Integer pageNumber : pageNumbers) {
            nodes.add(new StructureNode("toc-page-" + pageNumber, "root", "SECTION", 1, "", "",
                    "", pageNumber, pageNumber, 0, 0, "LAYOUT", .9D, false,
                    "{\"kind\":\"TABLE_OF_CONTENTS\"}"));
        }
        return new ParsedDocument(parsed.pages(), parsed.blocks(), List.copyOf(nodes), parsed.warnings());
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
                        blocks.add(new DocumentBlock(BlockType.HEADING, 1, 1, sectionPath(headings), "root", value,
                                0, value.length(), new BlockStyle(headingLevel, true, 0F, 0F, 0F), false));
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
            return structured(pages, blocks, List.of(), "DOCX_STYLE");
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
        return structured(List.of(new Page(1, "", text)), blocks, List.of(), "FALLBACK");
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
                blocks.add(new DocumentBlock(BlockType.HEADING, 1, 1, sectionPath(headings), "root", value,
                        0, value.length(), new BlockStyle(level, true, 0F, 0F, 0F), false));
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
        return structured(List.of(new Page(1, sectionPath(headings), text)), blocks, List.of(), "MARKDOWN_HEADING");
    }

    private ParsedDocument structured(List<Page> pages, List<DocumentBlock> input, List<String> inputWarnings,
                                      String detectionSource) {
        List<StructureNode> nodes = new ArrayList<>();
        nodes.add(StructureNode.root(pages));
        List<DocumentBlock> blocks = new ArrayList<>();
        List<String> warnings = new ArrayList<>(inputWarnings);
        Map<Integer, StructureNode> hierarchy = new LinkedHashMap<>();
        String currentNodeKey = "root";
        String currentPath = "";
        int charOffset = 0;
        int nodeIndex = 0;
        for (DocumentBlock block : input) {
            if (block.type() == BlockType.HEADING) {
                int level = headingLevel(block);
                hierarchy.entrySet().removeIf(entry -> entry.getKey() >= level);
                String nodeKey = "node-" + (++nodeIndex);
                StructureNode parent = hierarchy.get(level - 1);
                String code = headingCode(block.text());
                String title = headingTitle(block.text(), code);
                currentPath = hierarchy.values().stream().map(node -> displayHeading(node.code(), node.title()))
                        .reduce((left, right) -> left + " / " + right).map(value -> value + " / ").orElse("")
                        + displayHeading(code, title);
                StructureNode node = new StructureNode(nodeKey, parent == null ? "root" : parent.nodeKey(),
                        nodeType(level), level, code, title, currentPath, block.pageFrom(), block.pageTo(),
                        charOffset, charOffset + block.text().length(), detectionSource,
                        "LAYOUT".equals(detectionSource) ? .72D : .98D, true, "{}");
                nodes.add(node);
                hierarchy.put(level, node);
                currentNodeKey = nodeKey;
                blocks.add(copyBlock(block, currentPath, currentNodeKey, charOffset));
            } else {
                blocks.add(copyBlock(block, currentPath, currentNodeKey, charOffset));
            }
            charOffset += block.text().length() + 2;
        }
        if (nodes.size() == 1 && !warnings.contains("STRUCTURE_DETECTION_FALLBACK")) {
            warnings.add("STRUCTURE_DETECTION_FALLBACK");
        }
        return new ParsedDocument(pages, blocks, expandNodeRanges(nodes, blocks), List.copyOf(warnings));
    }

    private List<StructureNode> expandNodeRanges(List<StructureNode> nodes, List<DocumentBlock> blocks) {
        Map<String, StructureNode> nodesByKey = nodes.stream()
                .collect(Collectors.toMap(StructureNode::nodeKey, node -> node));
        return nodes.stream().map(node -> {
            if (node.level() == 0) return node;
            List<DocumentBlock> nodeBlocks = blocks.stream()
                    .filter(block -> isDescendantOrSelf(block.nodeKey(), node.nodeKey(), nodesByKey))
                    .toList();
            if (nodeBlocks.isEmpty()) return node;
            return new StructureNode(node.nodeKey(), node.parentNodeKey(), node.type(), node.level(), node.code(),
                    node.title(), node.sectionPath(),
                    nodeBlocks.stream().mapToInt(DocumentBlock::pageFrom).min().orElse(node.pageFrom()),
                    nodeBlocks.stream().mapToInt(DocumentBlock::pageTo).max().orElse(node.pageTo()),
                    nodeBlocks.stream().mapToInt(DocumentBlock::charStart).min().orElse(node.charStart()),
                    nodeBlocks.stream().mapToInt(DocumentBlock::charEnd).max().orElse(node.charEnd()),
                    node.detectionSource(), node.confidence(), node.indexable(), node.metadataJson());
        }).toList();
    }

    private boolean isDescendantOrSelf(String candidateKey, String nodeKey,
                                       Map<String, StructureNode> nodesByKey) {
        String currentKey = candidateKey;
        while (currentKey != null && !currentKey.isBlank()) {
            if (currentKey.equals(nodeKey)) return true;
            StructureNode current = nodesByKey.get(currentKey);
            currentKey = current == null ? "" : current.parentNodeKey();
        }
        return false;
    }

    private DocumentBlock copyBlock(DocumentBlock block, String sectionPath, String nodeKey, int charOffset) {
        return new DocumentBlock(block.type(), block.pageFrom(), block.pageTo(), sectionPath, nodeKey, block.text(),
                charOffset, charOffset + block.text().length(), block.style(), block.crossPageContinuation());
    }

    private int headingLevel(DocumentBlock block) {
        if (block.style().fontSize() > 0 && block.style().fontSize() <= 6) {
            return Math.max(1, Math.min(6, Math.round(block.style().fontSize())));
        }
        String code = headingCode(block.text());
        if (!code.isBlank()) return Math.max(1, Math.min(6, (int) code.chars().filter(value -> value == '.').count() + 1));
        return 1;
    }

    private String headingCode(String text) {
        if (text == null) return "";
        String value = text.stripLeading();
        int end = 0;
        while (end < value.length()) {
            char current = value.charAt(end);
            if (!Character.isDigit(current) && current != '.') break;
            end++;
        }
        String code = value.substring(0, end);
        return code.endsWith(".") ? code.substring(0, code.length() - 1) : code;
    }

    private String headingTitle(String text, String code) {
        if (code.isBlank()) return text.trim();
        return text.trim().substring(Math.min(text.trim().length(), code.length())).replaceFirst("^[.、)）\\s]+", "").trim();
    }

    private String displayHeading(String code, String title) {
        return code == null || code.isBlank() ? title : code + " " + title;
    }

    private String nodeType(int level) {
        return switch (level) {
            case 1 -> "CHAPTER";
            case 2 -> "SECTION";
            default -> "SUBSECTION";
        };
    }

    private float medianPdfFontSize(PdfLoader.PdfDocument pdf) {
        List<Float> sizes = pdf.elements().stream().map(PdfLoader.TextElement::fontSize)
                .filter(value -> value > 0).sorted().toList();
        return sizes.isEmpty() ? 0F : sizes.get(sizes.size() / 2);
    }

    private float pdfFontSize(PdfLoader.PdfDocument pdf, int page, String text) {
        return pdf.pages().stream().filter(value -> value.number() == page).findFirst()
                .flatMap(value -> value.elements().stream()
                        .collect(Collectors.groupingBy(PdfLoader.TextElement::lineIndex))
                        .values().stream()
                        .filter(line -> normalize(line.stream().map(PdfLoader.TextElement::text)
                                .reduce("", String::concat)).equals(text))
                        .findFirst()
                        .map(line -> line.stream().map(PdfLoader.TextElement::fontSize)
                                .max(Float::compareTo).orElse(0F)))
                .orElse(0F);
    }

    private boolean isPdfHeading(String text, float fontSize, float medianFontSize) {
        if (text == null || text.isBlank() || text.length() > 120 || medianFontSize <= 0F) return false;
        boolean visuallyProminent = fontSize >= medianFontSize * 1.15F;
        boolean numberedCandidate = !headingCode(text).isBlank() && fontSize >= medianFontSize;
        return visuallyProminent || numberedCandidate;
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

    static String normalizePdfParagraph(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder result = new StringBuilder();
        String previousLine = "";
        for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1)) {
            String line = rawLine.replaceAll("[ \\t]+", " ").trim();
            if (line.isBlank()) {
                if (!result.isEmpty() && !result.toString().endsWith("\n\n")) {
                    result.append("\n\n");
                }
                previousLine = "";
                continue;
            }
            if (result.isEmpty()) {
                result.append(line);
            } else if (isStandaloneListMarker(previousLine)) {
                result.append(line);
            } else if (startsPdfParagraph(line)) {
                result.append("\n\n").append(line);
            } else if (needsSpaceBetween(previousLine, line)) {
                result.append(' ').append(line);
            } else {
                result.append(line);
            }
            previousLine = line;
        }
        return result.toString().trim();
    }

    private static boolean startsPdfParagraph(String line) {
        return PDF_LIST_MARKER.matcher(line).find();
    }

    private static boolean isStandaloneListMarker(String line) {
        return line.matches("^(?:\\d+|[一二三四五六七八九十]+)[.)．、]$|^[（(][一二三四五六七八九十\\d]+[）)]$");
    }

    private static boolean needsSpaceBetween(String previousLine, String line) {
        if (previousLine.isEmpty() || line.isEmpty()) return false;
        char left = previousLine.charAt(previousLine.length() - 1);
        char right = line.charAt(0);
        return isAsciiWord(left) && isAsciiWord(right);
    }

    private static boolean isAsciiWord(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9');
    }

    private record OutlineHeading(int level) {
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
