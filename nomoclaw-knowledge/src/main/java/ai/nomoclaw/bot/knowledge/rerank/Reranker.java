package ai.nomoclaw.bot.knowledge.rerank;

import java.util.List;

/**
 * Scores first-stage retrieval candidates against a user query.
 */
public interface Reranker {
    /**
     * Scores every candidate in one batch.
     *
     * @param query the original user query
     * @param candidates first-stage candidates in RRF order
     * @return scores keyed by candidate identifier
     */
    RerankResult rerank(String query, List<RerankCandidate> candidates);

    /**
     * Indicates whether the configured reranker can be used now.
     *
     * @return {@code true} when reranking can be attempted
     */
    boolean available();

    /**
     * A first-stage result represented as text for a scoring model.
     */
    record RerankCandidate(String id, String text) {
    }

    /**
     * A model score associated with a candidate.
     */
    record RerankScore(String id, double score) {
    }

    /**
     * The complete batch-scoring result.
     */
    record RerankResult(List<RerankScore> scores) {
    }
}
