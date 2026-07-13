package ai.nomoclaw.bot.knowledge.ingestion;

import java.util.List;

/**
 * Splits parsed documents into embedding-sized chunks.
 */
public interface DocumentChunker {
    /**
     * Splits parsed document content into overlapping embedding-sized chunks.
     */
    List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens);

    record Chunk(int index, String content, int tokenCount, int pageFrom, int pageTo, String section, int charStart,
                 int charEnd) {
    }
}
