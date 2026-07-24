package ai.nomoclaw.bot.knowledge.bm25;

import java.util.List;
import java.util.Map;

/**
 * Thin Elasticsearch gateway used by the BM25 store.
 */
public interface ElasticsearchBm25Client {
    void ensureIndex(String indexName);

    void bulkIndex(String indexName, List<IndexDocument> documents);

    List<SearchDocumentHit> search(String indexName, String query, List<String> knowledgeBaseUids,
                                   int limit, List<String> sourceFields);

    void deleteByQuery(String indexName, QuerySpec querySpec);

    boolean indexExists(String indexName);

    /**
     * A document to be indexed into Elasticsearch.
     */
    record IndexDocument(String id, Map<String, Object> source) {
    }

    /**
     * Lightweight search hit used by the BM25 store.
     */
    record SearchDocumentHit(String chunkUid, double score) {
    }

    /**
     * Minimal query model shared by the store and the Elasticsearch client adapter.
     */
    sealed interface QuerySpec permits QuerySpec.BoolQuerySpec, QuerySpec.MatchAllQuerySpec,
            QuerySpec.MultiMatchQuerySpec, QuerySpec.TermQuerySpec, QuerySpec.TermsQuerySpec {
        /**
         * Boolean query.
         */
        record BoolQuerySpec(List<QuerySpec> must, List<QuerySpec> filter) implements QuerySpec {
        }

        /**
         * Match-all query.
         */
        record MatchAllQuerySpec() implements QuerySpec {
        }

        /**
         * Multi-field full-text query.
         */
        record MultiMatchQuerySpec(String query, List<String> fields) implements QuerySpec {
        }

        /**
         * Exact match query on one field.
         */
        record TermQuerySpec(String field, String value) implements QuerySpec {
        }

        /**
         * Terms query on one field.
         */
        record TermsQuerySpec(String field, List<String> values) implements QuerySpec {
        }
    }
}
