package ai.nomoclaw.bot.knowledge.bm25;

import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.IndexDocument;
import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.QuerySpec;
import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.SearchDocumentHit;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Official Elasticsearch Java API Client adapter for the BM25 store.
 */
@Component
public class OfficialElasticsearchBm25Client implements ElasticsearchBm25Client {
    private static final Logger log = LoggerFactory.getLogger(OfficialElasticsearchBm25Client.class);

    private final ElasticsearchClient client;

    public OfficialElasticsearchBm25Client(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void ensureIndex(String indexName) {
        try {
            client.indices().create(create -> create
                    .index(indexName)
                    .settings(settings -> settings
                            .analysis(analysis -> analysis
                                    .filter("bm25_ngram", filter -> filter
                                            .definition(definition -> definition
                                                    .ngram(ngram -> ngram.minGram(2).maxGram(3))))
                                    .analyzer("bm25_index_analyzer", analyzer -> analyzer
                                            .custom(custom -> custom
                                                    .tokenizer("standard")
                                                    .filter("lowercase", "cjk_width", "bm25_ngram")))
                                    .analyzer("bm25_search_analyzer", analyzer -> analyzer
                                            .custom(custom -> custom
                                                    .tokenizer("standard")
                                                    .filter("lowercase", "cjk_width")))))
                    .mappings(mappings -> mappings
                            .properties("chunkUid", keywordProperty())
                            .properties("knowledgeBaseUid", keywordProperty())
                            .properties("documentUid", keywordProperty())
                            .properties("documentVersionUid", keywordProperty())
                            .properties("documentName", textProperty())
                            .properties("sectionPath", textProperty())
                            .properties("content", textProperty())));
        } catch (Exception ex) {
            if (!indexAlreadyExists(ex)) {
                throw new IllegalStateException("创建知识库 BM25 ES 索引失败", ex);
            }
        }
    }

    @Override
    public void bulkIndex(String indexName, List<IndexDocument> documents) {
        if (documents == null || documents.isEmpty()) return;
        try {
            BulkRequest.Builder builder = new BulkRequest.Builder().index(indexName).refresh(Refresh.True);
            for (IndexDocument document : documents) {
                builder.operations(BulkOperation.of(operation -> operation
                        .index(index -> index
                                .id(document.id())
                                .document(toJsonNode(document.source())))));
            }
            if (builder.build().operations().isEmpty()) {
                return;
            }
            var response = client.bulk(builder.build());
            if (response.errors()) {
                throw new IllegalStateException("写入知识库 BM25 ES 索引失败: bulk 返回 errors=true");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("写入知识库 BM25 ES 索引失败", ex);
        }
    }

    @Override
    public List<SearchDocumentHit> search(String indexName, String query, List<String> knowledgeBaseUids,
                                          int limit, List<String> sourceFields) {
        try {
            SearchResponse<JsonNode> response = client.search(search -> search
                            .index(indexName)
                            .size(limit)
                            .source(source -> source.filter(filter -> filter.includes(sourceFields)))
                            .query(toQuery(new QuerySpec.BoolQuerySpec(
                                    List.of(new QuerySpec.MultiMatchQuerySpec(query, List.of(
                                            "documentName^3", "sectionPath^2", "content"))),
                                    List.of(new QuerySpec.TermsQuerySpec("knowledgeBaseUid", knowledgeBaseUids))))),
                    JsonNode.class);
            return response.hits().hits().stream()
                    .map(this::toSearchHit)
                    .filter(hit -> hit != null)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("查询知识库 BM25 ES 索引失败", ex);
        }
    }

    @Override
    public void deleteByQuery(String indexName, QuerySpec querySpec) {
        try {
            var response = client.deleteByQuery(delete -> delete
                    .index(indexName)
                    .refresh(true)
                    .query(toQuery(querySpec)));
            if (response.failures() != null && !response.failures().isEmpty()) {
                throw new IllegalStateException("删除知识库 BM25 ES 索引失败: delete_by_query 返回 failures");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("删除知识库 BM25 ES 索引失败", ex);
        }
    }

    @Override
    public boolean indexExists(String indexName) {
        try {
            return client.indices().exists(ExistsRequest.of(exists -> exists.index(indexName))).value();
        } catch (Exception ex) {
            log.debug("Elasticsearch index existence probe failed index={}", indexName, ex);
            return false;
        }
    }

    private SearchDocumentHit toSearchHit(Hit<JsonNode> hit) {
        JsonNode source = hit.source();
        if (source == null) {
            return null;
        }
        String chunkUid = source.path("chunkUid").asText("");
        if (chunkUid.isBlank()) {
            return null;
        }
        return new SearchDocumentHit(chunkUid, hit.score() == null ? 0D : hit.score());
    }

    private Query toQuery(QuerySpec querySpec) {
        return switch (querySpec) {
            case QuerySpec.BoolQuerySpec bool -> Query.of(query -> query.bool(value -> value
                    .must(bool.must().stream().map(this::toQuery).toList())
                    .filter(bool.filter().stream().map(this::toQuery).toList())));
            case QuerySpec.MatchAllQuerySpec ignored -> Query.of(query -> query.matchAll(matchAll -> matchAll));
            case QuerySpec.MultiMatchQuerySpec multiMatch -> Query.of(query -> query.multiMatch(value -> value
                    .query(multiMatch.query())
                    .fields(multiMatch.fields())));
            case QuerySpec.TermQuerySpec term -> Query.of(query -> query.term(value -> value
                    .field(term.field())
                    .value(term.value())));
            case QuerySpec.TermsQuerySpec terms -> Query.of(query -> query.terms(value -> value
                    .field(terms.field())
                    .terms(fieldValues -> fieldValues.value(
                            terms.values().stream().map(FieldValue::of).toList()))));
        };
    }

    private Property keywordProperty() {
        return Property.of(property -> property.keyword(keyword -> keyword));
    }

    private Property textProperty() {
        return Property.of(property -> property.text(text -> text
                .analyzer("bm25_index_analyzer")
                .searchAnalyzer("bm25_search_analyzer")));
    }

    private JsonNode toJsonNode(Map<String, Object> source) {
        Map<String, Object> ordered = new LinkedHashMap<>(source);
        return ai.nomoclaw.bot.knowledge.util.JsonUtil.mapper().valueToTree(ordered);
    }

    private boolean indexAlreadyExists(Exception exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("resource_already_exists_exception")
                || message.contains("index_already_exists_exception"));
    }
}
