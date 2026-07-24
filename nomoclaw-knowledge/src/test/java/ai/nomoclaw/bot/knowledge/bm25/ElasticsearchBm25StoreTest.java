package ai.nomoclaw.bot.knowledge.bm25;

import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.IndexDocument;
import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.QuerySpec;
import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.SearchDocumentHit;
import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchBm25StoreTest {
    @Test
    void shouldIndexSearchAndDeleteDocument() {
        FakeElasticsearchBm25Client fake = new FakeElasticsearchBm25Client(false);
        KnowledgeProperties properties = properties();
        ElasticsearchBm25Store store = new ElasticsearchBm25Store(properties, fake);

        assertThat(store.available()).isTrue();

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
    }

    @Test
    void shouldDisableBm25WhenElasticsearchUnavailable() {
        ElasticsearchBm25Store store = new ElasticsearchBm25Store(properties(),
                new FakeElasticsearchBm25Client(true));

        assertThat(store.available()).isFalse();
        assertThat(store.search("餐补", List.of("kb_employee"), 10)).isEmpty();
    }

    private KnowledgeProperties properties() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getRetrieval().getBm25().setBackend("elasticsearch");
        properties.getRetrieval().getBm25().setEnabled(true);
        properties.getRetrieval().getBm25().getElasticsearch().setIndexName("bm25_test");
        return properties;
    }

    private static final class FakeElasticsearchBm25Client implements ElasticsearchBm25Client {
        private final Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        private boolean indexCreated;
        private final boolean unavailable;

        private FakeElasticsearchBm25Client(boolean unavailable) {
            this.unavailable = unavailable;
        }

        @Override
        public void ensureIndex(String indexName) {
            if (unavailable) {
                throw new IllegalStateException("simulated unavailable");
            }
            indexCreated = true;
        }

        @Override
        public void bulkIndex(String indexName, List<IndexDocument> documents) {
            for (IndexDocument document : documents) {
                this.documents.put(document.id(), document.source());
            }
        }

        @Override
        public List<SearchDocumentHit> search(String indexName, String query, List<String> knowledgeBaseUids,
                                              int limit, List<String> sourceFields) {
            return documents.values().stream()
                    .filter(document -> knowledgeBaseUids.contains(document.get("knowledgeBaseUid")))
                    .map(document -> new SearchDocumentHit(String.valueOf(document.get("chunkUid")),
                            score(document, query)))
                    .filter(hit -> hit.score() > 0)
                    .sorted((left, right) -> Double.compare(right.score(), left.score()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void deleteByQuery(String indexName, QuerySpec querySpec) {
            if (querySpec instanceof QuerySpec.MatchAllQuerySpec) {
                documents.clear();
                return;
            }
            if (!(querySpec instanceof QuerySpec.TermQuerySpec termQuery)) {
                throw new IllegalArgumentException("Unsupported fake query type: " + querySpec.getClass().getSimpleName());
            }
            Iterator<Map.Entry<String, Map<String, Object>>> iterator = documents.entrySet().iterator();
            while (iterator.hasNext()) {
                Map<String, Object> document = iterator.next().getValue();
                if (termQuery.value().equals(String.valueOf(document.get(termQuery.field())))) {
                    iterator.remove();
                }
            }
        }

        @Override
        public boolean indexExists(String indexName) {
            return !unavailable && indexCreated;
        }

        private double score(Map<String, Object> document, String text) {
            String normalizedQuery = text.toLowerCase(Locale.ROOT);
            double score = 0;
            score += scoreField(document.get("documentName"), normalizedQuery, 3);
            score += scoreField(document.get("sectionPath"), normalizedQuery, 2);
            score += scoreField(document.get("content"), normalizedQuery, 1);
            return score;
        }

        private double scoreField(Object value, String normalizedQuery, int weight) {
            if (value == null) {
                return 0;
            }
            String normalizedValue = String.valueOf(value).toLowerCase(Locale.ROOT);
            int occurrences = 0;
            int from = 0;
            while (true) {
                int index = normalizedValue.indexOf(normalizedQuery, from);
                if (index < 0) {
                    break;
                }
                occurrences++;
                from = index + normalizedQuery.length();
            }
            return occurrences * weight;
        }
    }
}
