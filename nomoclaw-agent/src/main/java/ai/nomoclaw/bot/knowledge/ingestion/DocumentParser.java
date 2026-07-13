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

    record ParsedDocument(List<Page> pages) {
    }

    record Page(int number, String section, String text) {
    }
}
