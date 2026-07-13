package ai.nomoclaw.bot.knowledge.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for PDF, DOCX, Markdown, and UTF-8 text documents.
 */
@Component
public class DefaultDocumentParser implements DocumentParser {
    @Override
    public boolean supports(String contentType, String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt") || name.endsWith(".md");
    }

    @Override
    public ParsedDocument parse(Path file) {
        try {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".pdf")) return parsePdf(file);
            if (name.endsWith(".docx")) return parseDocx(file);
            return parseText(file);
        } catch (KnowledgeParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new KnowledgeParseException("PARSE_FAILED", "文档解析失败: " + ex.getMessage(), ex);
        }
    }

    private ParsedDocument parsePdf(Path file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            if (document.isEncrypted()) throw new KnowledgeParseException("ENCRYPTED_PDF", "不支持加密 PDF");
            PDFTextStripper stripper = new PDFTextStripper();
            List<Page> pages = new ArrayList<>();
            int characters = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                characters += text.length();
                pages.add(new Page(page, "", text));
            }
            if (characters < 20)
                throw new KnowledgeParseException("OCR_REQUIRED", "PDF 未提取到足够文本，可能是扫描件，需要 OCR");
            return new ParsedDocument(pages);
        }
    }

    private ParsedDocument parseDocx(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file); XWPFDocument document = new XWPFDocument(input)) {
            List<Page> pages = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            String section = "";
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String value = normalize(paragraph.getText());
                if (value.isBlank()) continue;
                String style = paragraph.getStyle();
                if (style != null && style.toLowerCase(Locale.ROOT).startsWith("heading")) section = value;
                text.append(value).append("\n\n");
            }
            if (text.isEmpty()) throw new KnowledgeParseException("EMPTY_DOCUMENT", "文档没有可索引文本");
            pages.add(new Page(1, section, text.toString().trim()));
            return new ParsedDocument(pages);
        }
    }

    private ParsedDocument parseText(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new KnowledgeParseException("INVALID_ENCODING", "文本文件必须使用 UTF-8 编码", ex);
        }
        text = normalize(text.replace("\uFEFF", ""));
        if (text.isBlank()) throw new KnowledgeParseException("EMPTY_DOCUMENT", "文档没有可索引文本");
        return new ParsedDocument(List.of(new Page(1, firstHeading(text), text)));
    }

    private String firstHeading(String text) {
        return text.lines().filter(line -> line.startsWith("#")).map(line -> line.replaceFirst("^#+\\s*", "")).findFirst().orElse("");
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[ \\t]+", " ").trim();
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
