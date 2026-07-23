package ai.nomoclaw.bot.knowledge.vector;

import java.util.List;
import java.util.Map;

/**
 * Dense vector index abstraction.
 */
public interface VectorStore {
    /**
     * Creates the collection if absent and validates its vector dimension.
     */
    void ensureCollection(String collection, int dimension);

    /**
     * Inserts or replaces points by their stable chunk IDs.
     */
    void upsert(String collection, List<Point> points);

    /**
     * Searches vectors while restricting candidates to the supplied knowledge bases.
     */
    List<Hit> search(String collection, List<Float> vector, List<String> knowledgeBaseUids, int limit);

    /**
     * Removes all vector points that belong to a document.
     */
    void deleteByDocument(String collection, String documentUid);

    /**
     * Removes all vector points that belong to one immutable document version.
     */
    void deleteByDocumentVersion(String collection, String documentVersionUid);

    /**
     * Returns whether the configured vector database is currently reachable.
     */
    boolean available();

    record Point(String id, List<Float> vector, Map<String, Object> payload) {
    }

    record Hit(String id, double score, Map<String, Object> payload) {
    }
}
