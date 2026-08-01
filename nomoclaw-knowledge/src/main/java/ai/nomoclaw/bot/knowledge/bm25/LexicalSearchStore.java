package ai.nomoclaw.bot.knowledge.bm25;

import java.util.ArrayList;
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
     * Opens an incremental version writer. Implementations may override this to avoid buffering all chunks.
     */
    default IndexSession beginDocumentVersion(String knowledgeBaseUid, String documentUid,
                                              String documentVersionUid, String documentName) {
        List<IndexedChunk> buffered = new ArrayList<>();
        return new IndexSession() {
            private boolean committed;

            @Override
            public void add(IndexedChunk chunk) {
                buffered.add(chunk);
            }

            @Override
            public void commit() {
                replaceDocument(knowledgeBaseUid, documentUid, documentVersionUid, documentName, buffered);
                committed = true;
            }

            @Override
            public void close() {
                if (!committed) buffered.clear();
            }
        };
    }

    /**
     * Finds lexical candidates restricted to the supplied knowledge bases.
     */
    List<Hit> search(String query, List<String> knowledgeBaseUids, int limit);

    /**
     * Deletes all lexical index records for a document.
     */
    void deleteByDocument(String documentUid);

    /**
     * Deletes lexical index records derived from one immutable document version.
     */
    default void deleteByDocumentVersion(String documentVersionUid) {
    }

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
    record IndexedChunk(String chunkUid, String sectionPath, String content, String nodeUid, String chapterCode,
                        String chapterTitle, String sectionCode, String sectionTitle, Integer pageFrom,
                        Integer pageTo, String chunkStrategy) {
        public IndexedChunk(String chunkUid, String sectionPath, String content) {
            this(chunkUid, sectionPath, content, "", "", "", "", "", null, null, "TOKEN");
        }
    }

    /**
     * Transaction-like writer for a single immutable document version.
     */
    interface IndexSession extends AutoCloseable {
        void add(IndexedChunk chunk);

        void commit();

        @Override
        void close();
    }

    /**
     * A lexical retrieval candidate and its raw relevance score.
     */
    record Hit(String chunkUid, double score) {
    }
}
