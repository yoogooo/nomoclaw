package ai.nomoclaw.bot.knowledge.ingestion;

import java.util.List;
import java.util.function.Consumer;

/**
 * Splits parsed documents into embedding-sized chunks.
 */
public interface DocumentChunker {
    /**
     * Splits parsed document content into overlapping embedding-sized chunks.
     */
    List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens);

    default List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens,
                              String strategy) {
        return split(document, sizeTokens, overlapTokens);
    }

    /**
     * Incrementally emits chunks so callers can keep only one downstream batch in memory.
     */
    default void split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens,
                       Consumer<Chunk> consumer) {
        split(document, sizeTokens, overlapTokens).forEach(consumer);
    }

    default void split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens,
                       String strategy, Consumer<Chunk> consumer) {
        split(document, sizeTokens, overlapTokens, strategy).forEach(consumer);
    }

    record Chunk(int index, String content, int tokenCount, int pageFrom, int pageTo, String section, int charStart,
                 int charEnd, String nodeKey) {
        public Chunk(int index, String content, int tokenCount, int pageFrom, int pageTo, String section,
                     int charStart, int charEnd) {
            this(index, content, tokenCount, pageFrom, pageTo, section, charStart, charEnd, "root");
        }
    }
}
