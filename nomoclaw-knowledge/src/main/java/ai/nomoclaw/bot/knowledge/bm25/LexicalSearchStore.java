package ai.nomoclaw.bot.knowledge.bm25;

import java.util.List;

/**
 * Persistent lexical index abstraction used for full-text retrieval.
 */
public interface LexicalSearchStore {
    /**
     * Replaces every indexed chunk for one document with its current ready version.
     */
    void replaceDocument(String knowledgeBaseUid, String documentUid, String documentVersionUid,
                         String documentName, List<IndexedChunk> chunks);

    /**
     * Finds lexical candidates restricted to the supplied knowledge bases.
     */
    List<Hit> search(String query, List<String> knowledgeBaseUids, int limit);

    /**
     * Deletes all lexical index records for a document.
     */
    void deleteByDocument(String documentUid);

    /**
     * Deletes all lexical index records for a knowledge base.
     */
    void deleteByKnowledgeBase(String knowledgeBaseUid);

    /**
     * Removes every derived lexical index record before a full rebuild.
     */
    void clear();

    /**
     * Returns whether the lexical index is enabled and usable.
     */
    boolean available();

    /**
     * A chunk prepared for lexical indexing.
     */
    record IndexedChunk(String chunkUid, String sectionPath, String content) {
    }

    /**
     * A lexical retrieval candidate and its raw relevance score.
     */
    record Hit(String chunkUid, double score) {
    }
}
