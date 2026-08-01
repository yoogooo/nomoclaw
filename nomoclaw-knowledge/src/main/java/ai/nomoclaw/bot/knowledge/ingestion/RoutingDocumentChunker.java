package ai.nomoclaw.bot.knowledge.ingestion;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Routes ingestion to the selected chunking strategy while retaining the legacy token chunker.
 */
@Primary
@Component
public class RoutingDocumentChunker implements DocumentChunker {
    private final RecursiveDocumentChunker tokenChunker;
    private final SmartDocumentChunker smartChunker;

    public RoutingDocumentChunker(RecursiveDocumentChunker tokenChunker, SmartDocumentChunker smartChunker) {
        this.tokenChunker = tokenChunker;
        this.smartChunker = smartChunker;
    }

    @Override
    public List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens) {
        return tokenChunker.split(document, sizeTokens, overlapTokens);
    }

    @Override
    public List<Chunk> split(DocumentParser.ParsedDocument document, int sizeTokens, int overlapTokens,
                             String strategy) {
        return "SMART".equals(normalize(strategy))
                ? smartChunker.split(document, sizeTokens, overlapTokens)
                : tokenChunker.split(document, sizeTokens, overlapTokens);
    }

    private String normalize(String strategy) {
        return strategy == null ? "TOKEN" : strategy.trim().toUpperCase(Locale.ROOT);
    }
}
