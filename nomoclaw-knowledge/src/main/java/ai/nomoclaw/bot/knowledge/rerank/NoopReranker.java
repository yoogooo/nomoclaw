package ai.nomoclaw.bot.knowledge.rerank;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Disabled reranker that keeps ONNX classes out of the application context.
 */
@Component
@ConditionalOnProperty(prefix = "knowledge.retrieval.rerank", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopReranker implements Reranker {
    @Override
    public RerankResult rerank(String query, List<RerankCandidate> candidates) {
        return new RerankResult(List.of());
    }

    @Override
    public boolean available() {
        return false;
    }
}
