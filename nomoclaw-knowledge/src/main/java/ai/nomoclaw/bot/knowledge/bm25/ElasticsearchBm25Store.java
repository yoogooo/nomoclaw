package ai.nomoclaw.bot.knowledge.bm25;

import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.IndexDocument;
import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.QuerySpec;
import ai.nomoclaw.bot.knowledge.bm25.ElasticsearchBm25Client.SearchDocumentHit;
import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch-backed implementation of the persistent BM25 lexical index.
 */
@Component
@ConditionalOnProperty(prefix = "knowledge.retrieval.bm25", name = "backend", havingValue = "elasticsearch", matchIfMissing = true)
public class ElasticsearchBm25Store implements LexicalSearchStore {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchBm25Store.class);
    private static final String FIELD_CHUNK_UID = "chunkUid";
    private static final String FIELD_KNOWLEDGE_BASE_UID = "knowledgeBaseUid";
    private static final String FIELD_DOCUMENT_UID = "documentUid";
    private static final String FIELD_DOCUMENT_VERSION_UID = "documentVersionUid";
    private static final String FIELD_DOCUMENT_NAME = "documentName";
    private static final String FIELD_SECTION_PATH = "sectionPath";
    private static final String FIELD_NODE_UID = "nodeUid";
    private static final String FIELD_SECTION_CODE = "sectionCode";
    private static final String FIELD_SECTION_TITLE = "sectionTitle";
    private static final String FIELD_CHAPTER_CODE = "chapterCode";
    private static final String FIELD_CHAPTER_TITLE = "chapterTitle";
    private static final String FIELD_PAGE_FROM = "pageFrom";
    private static final String FIELD_PAGE_TO = "pageTo";
    private static final String FIELD_CHUNK_STRATEGY = "chunkStrategy";
    private static final String FIELD_CONTENT = "content";

    private final ElasticsearchBm25Client client;
    private final String indexName;
    private final boolean enabled;

    public ElasticsearchBm25Store(KnowledgeProperties properties, ElasticsearchBm25Client client) {
        this.client = client;
        indexName = properties.getRetrieval().getBm25().getElasticsearch().getIndexName();
        boolean initializedEnabled = false;
        try {
            if (!properties.getRetrieval().getBm25().isEnabled()) {
                log.info("[KnowledgeSearch][BM25][ES] disabled by configuration");
            } else {
                client.ensureIndex(indexName);
                initializedEnabled = true;
                log.info("[KnowledgeSearch][BM25][ES] initialized index={}", indexName);
            }
        } catch (Exception ex) {
            log.warn("[KnowledgeSearch][BM25][ES] initialization failed, lexical retrieval disabled", ex);
        }
        enabled = initializedEnabled;
    }

    @Override
    public void replaceDocument(String knowledgeBaseUid, String documentUid, String documentVersionUid,
                                String documentName, List<IndexedChunk> chunks) {
        if (!enabled) return;
        deleteByDocumentVersion(documentVersionUid);
        if (chunks == null || chunks.isEmpty()) return;
        client.bulkIndex(indexName, chunks.stream()
                .map(chunk -> new IndexDocument(chunk.chunkUid(),
                        documentBody(knowledgeBaseUid, documentUid, documentVersionUid, documentName, chunk)))
                .toList());
        log.info("[KnowledgeSearch][BM25][ES] indexed knowledgeBaseUid={} documentUid={} chunkCount={}",
                knowledgeBaseUid, documentUid, chunks.size());
    }

    @Override
    public IndexSession beginDocumentVersion(String knowledgeBaseUid, String documentUid,
                                             String documentVersionUid, String documentName) {
        if (!enabled) return LexicalSearchStore.super.beginDocumentVersion(
                knowledgeBaseUid, documentUid, documentVersionUid, documentName);
        deleteByDocumentVersion(documentVersionUid);
        return new BufferedIndexSession(knowledgeBaseUid, documentUid, documentVersionUid, documentName);
    }

    @Override
    public List<Hit> search(String query, List<String> knowledgeBaseUids, int limit) {
        if (!available() || query == null || query.isBlank() || knowledgeBaseUids == null || knowledgeBaseUids.isEmpty()) {
            return List.of();
        }
        List<SearchDocumentHit> results = client.search(indexName, query, knowledgeBaseUids, limit, List.of(FIELD_CHUNK_UID));
        List<Hit> hits = results.stream().map(hit -> new Hit(hit.chunkUid(), hit.score())).toList();
        log.info("[KnowledgeSearch][BM25][ES] knowledgeBaseCount={} limit={} candidateCount={} scores={}",
                knowledgeBaseUids.size(), limit, hits.size(), hits.stream().map(Hit::score).toList());
        return hits;
    }

    @Override
    public void deleteByDocument(String documentUid) {
        deleteByQuery(new QuerySpec.TermQuerySpec(FIELD_DOCUMENT_UID, documentUid));
    }

    @Override
    public void deleteByDocumentVersion(String documentVersionUid) {
        deleteByQuery(new QuerySpec.TermQuerySpec(FIELD_DOCUMENT_VERSION_UID, documentVersionUid));
    }

    @Override
    public void deleteByKnowledgeBase(String knowledgeBaseUid) {
        deleteByQuery(new QuerySpec.TermQuerySpec(FIELD_KNOWLEDGE_BASE_UID, knowledgeBaseUid));
    }

    @Override
    public void clear() {
        if (!enabled) return;
        deleteByQuery(new QuerySpec.MatchAllQuerySpec());
    }

    @Override
    public boolean available() {
        return enabled && client.indexExists(indexName);
    }

    private void deleteByQuery(QuerySpec querySpec) {
        if (!enabled) return;
        client.deleteByQuery(indexName, querySpec);
    }

    private Map<String, Object> documentBody(String knowledgeBaseUid, String documentUid, String documentVersionUid,
                                             String documentName, IndexedChunk chunk) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(FIELD_CHUNK_UID, chunk.chunkUid());
        body.put(FIELD_KNOWLEDGE_BASE_UID, knowledgeBaseUid);
        body.put(FIELD_DOCUMENT_UID, documentUid);
        body.put(FIELD_DOCUMENT_VERSION_UID, documentVersionUid);
        body.put(FIELD_DOCUMENT_NAME, safe(documentName));
        body.put(FIELD_SECTION_PATH, safe(chunk.sectionPath()));
        body.put(FIELD_NODE_UID, safe(chunk.nodeUid()));
        body.put(FIELD_CHAPTER_CODE, safe(chunk.chapterCode()));
        body.put(FIELD_CHAPTER_TITLE, safe(chunk.chapterTitle()));
        body.put(FIELD_SECTION_CODE, safe(chunk.sectionCode()));
        body.put(FIELD_SECTION_TITLE, safe(chunk.sectionTitle()));
        body.put(FIELD_PAGE_FROM, chunk.pageFrom());
        body.put(FIELD_PAGE_TO, chunk.pageTo());
        body.put(FIELD_CHUNK_STRATEGY, safe(chunk.chunkStrategy()));
        body.put("visibility", "INHERIT");
        body.put("departmentUids", List.of());
        body.put("principalUids", List.of());
        body.put("accessScopeVersion", 0);
        body.put(FIELD_CONTENT, safe(chunk.content()));
        return body;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private final class BufferedIndexSession implements IndexSession {
        private final String knowledgeBaseUid;
        private final String documentUid;
        private final String documentVersionUid;
        private final String documentName;
        private final List<IndexedChunk> buffered;
        private boolean committed;

        private BufferedIndexSession(String knowledgeBaseUid, String documentUid,
                                     String documentVersionUid, String documentName) {
            this.knowledgeBaseUid = knowledgeBaseUid;
            this.documentUid = documentUid;
            this.documentVersionUid = documentVersionUid;
            this.documentName = documentName;
            buffered = new ArrayList<>();
        }

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
            if (!committed) {
                buffered.clear();
            }
        }
    }
}
