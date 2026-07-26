package ai.nomoclaw.bot.knowledge.bm25;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import ai.nomoclaw.bot.knowledge.config.KnowledgePropertiesTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneBm25StoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldIndexChineseContentFilterByKnowledgeBaseAndDeleteDocument() {
        KnowledgeProperties properties = KnowledgePropertiesTestSupport.properties();
        properties.setStorageRoot(temporaryDirectory);
        properties.getRetrieval().getBm25().setIndexPath(temporaryDirectory.resolve("bm25"));
        LuceneBm25Store store = new LuceneBm25Store(properties);
        try {
            store.replaceDocument("kb_employee", "doc_employee", "ver_1", "员工手册.pdf", List.of(
                    new LexicalSearchStore.IndexedChunk("chunk_allowance", "薪酬 / 补贴", "员工每月可享受餐补。"),
                    new LexicalSearchStore.IndexedChunk("chunk_attendance", "考勤", "员工应按照规定完成打卡。")));
            store.replaceDocument("kb_other", "doc_other", "ver_2", "其他资料", List.of(
                    new LexicalSearchStore.IndexedChunk("chunk_other", "无关", "餐补关键词不应跨知识库泄露。")));

            List<LexicalSearchStore.Hit> hits = store.search("餐补", List.of("kb_employee"), 10);

            assertThat(hits).extracting(LexicalSearchStore.Hit::chunkUid).containsExactly("chunk_allowance");
            assertThat(hits.getFirst().score()).isPositive();

            store.deleteByDocument("doc_employee");

            assertThat(store.search("餐补", List.of("kb_employee"), 10)).isEmpty();
        } finally {
            store.close();
        }
    }

    @Test
    void shouldDisableBm25WhenIndexInitializationFails() throws IOException {
        Path indexFile = temporaryDirectory.resolve("bm25-file");
        Files.writeString(indexFile, "occupied");

        KnowledgeProperties properties = KnowledgePropertiesTestSupport.properties();
        properties.setStorageRoot(temporaryDirectory);
        properties.getRetrieval().getBm25().setIndexPath(indexFile);

        LuceneBm25Store store = new LuceneBm25Store(properties);
        try {
            assertThat(store.available()).isFalse();
            assertThat(store.search("餐补", List.of("kb_employee"), 10)).isEmpty();
        } finally {
            store.close();
        }
    }
}
