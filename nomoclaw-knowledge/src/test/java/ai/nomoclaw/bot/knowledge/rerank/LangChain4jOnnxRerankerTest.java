package ai.nomoclaw.bot.knowledge.rerank;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jOnnxRerankerTest {
    @TempDir
    Path tempDir;

    @Test
    void warmupDoesNotLoadModelWhenDisabled() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties();
        Path model = tempDir.resolve("model.onnx");
        Path tokenizer = tempDir.resolve("tokenizer.json");
        Files.writeString(model, "not a real model");
        Files.writeString(tokenizer, "{}");
        KnowledgeProperties.Rerank rerank = properties.getRetrieval().getRerank();
        rerank.setEnabled(true);
        rerank.setWarmupEnabled(false);
        rerank.setProvider("onnx");
        rerank.setModelPath(model);
        rerank.setTokenizerPath(tokenizer);

        LangChain4jOnnxReranker onnxReranker = new LangChain4jOnnxReranker(properties, Runnable::run);

        onnxReranker.warmup();

        assertThat(onnxReranker.available()).isTrue();
        assertThat(onnxReranker.loaded()).isFalse();
    }
}
