package ai.nomoclaw.bot.knowledge.rerank;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Local ONNX implementation backed by LangChain4j's scoring-model abstraction.
 */
@Component
@ConditionalOnProperty(prefix = "knowledge.retrieval.rerank", name = "enabled", havingValue = "true")
public class LangChain4jOnnxReranker implements Reranker {
    private static final Logger log = LoggerFactory.getLogger(LangChain4jOnnxReranker.class);

    private final KnowledgeProperties properties;
    private final Executor executor;
    private volatile OnnxScoringModel scoringModel;

    public LangChain4jOnnxReranker(KnowledgeProperties properties,
                                   @Qualifier("knowledgeRerankExecutor") Executor executor) {
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * Primes ONNX model loading and the first inference pass before serving requests.
     */
    @PostConstruct
    public void warmup() {
        if (!properties.getRetrieval().getRerank().isWarmupEnabled()) return;
        if (!available()) return;
        try {
            scoringModel().scoreAll(List.of(TextSegment.from("warmup document")), "warmup query").content();
            log.info("[KnowledgeSearch][Rerank] ONNX reranker warmup completed");
        } catch (Exception ex) {
            log.warn("[KnowledgeSearch][Rerank] ONNX reranker warmup failed; requests will retry on demand", ex);
        }
    }

    @Override
    public RerankResult rerank(String query, List<RerankCandidate> candidates) {
        if (!available()) throw new IllegalStateException("ONNX reranker is unavailable");
        if (candidates.isEmpty()) return new RerankResult(List.of());
        try {
            ScoringModel model = scoringModel();
            List<Double> scores = CompletableFuture.supplyAsync(() -> model.scoreAll(
                            candidates.stream().map(candidate -> TextSegment.from(candidate.text())).toList(), query).content(), executor)
                    .get(properties.getRetrieval().getRerank().getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (scores.size() != candidates.size()) {
                throw new IllegalStateException("ONNX reranker returned an unexpected score count");
            }
            List<RerankScore> result = new ArrayList<>(scores.size());
            for (int index = 0; index < scores.size(); index++) {
                Double score = scores.get(index);
                if (score == null || !Double.isFinite(score)) {
                    throw new IllegalStateException("ONNX reranker returned an invalid score");
                }
                result.add(new RerankScore(candidates.get(index).id(), score));
            }
            return new RerankResult(List.copyOf(result));
        } catch (Exception ex) {
            throw new IllegalStateException("ONNX reranker scoring failed", ex);
        }
    }

    @Override
    public boolean available() {
        KnowledgeProperties.Rerank rerank = properties.getRetrieval().getRerank();
        return rerank.isEnabled() && "onnx".equalsIgnoreCase(rerank.getProvider())
                && exists(rerank.getModelPath()) && exists(rerank.getTokenizerPath());
    }

    private ScoringModel scoringModel() {
        OnnxScoringModel current = scoringModel;
        if (current != null) return current;
        synchronized (this) {
            if (scoringModel == null) {
                KnowledgeProperties.Rerank rerank = properties.getRetrieval().getRerank();
                scoringModel = new OnnxScoringModel(rerank.getModelPath().toString(), rerank.getTokenizerPath().toString());
            }
            return scoringModel;
        }
    }

    boolean loaded() {
        return scoringModel != null;
    }

    private boolean exists(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    /**
     * Releases the native ONNX session when the application context stops.
     */
    @PreDestroy
    public void close() {
        OnnxScoringModel current = scoringModel;
        if (current != null) current.close();
    }
}
