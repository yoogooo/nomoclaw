package ai.nomoclaw.bot.knowledge.bm25;

import ai.nomoclaw.bot.knowledge.config.KnowledgeProperties;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Embedded Lucene implementation of the persistent BM25 lexical index.
 */
@Component
public class LuceneBm25Store implements LexicalSearchStore {
    private static final Logger log = LoggerFactory.getLogger(LuceneBm25Store.class);
    private static final String FIELD_CHUNK_UID = "chunkUid";
    private static final String FIELD_KNOWLEDGE_BASE_UID = "knowledgeBaseUid";
    private static final String FIELD_DOCUMENT_UID = "documentUid";
    private static final String FIELD_DOCUMENT_VERSION_UID = "documentVersionUid";
    private static final String FIELD_DOCUMENT_NAME = "documentName";
    private static final String FIELD_SECTION_PATH = "sectionPath";
    private static final String FIELD_CONTENT = "content";

    private final boolean enabled;
    private final Analyzer analyzer;
    private final Directory directory;
    private final IndexWriter writer;

    public LuceneBm25Store(KnowledgeProperties properties) {
        enabled = properties.getRetrieval().getBm25().isEnabled();
        analyzer = new SmartChineseAnalyzer();
        try {
            Path configuredPath = properties.getRetrieval().getBm25().getIndexPath();
            Path indexPath = configuredPath == null ? properties.getStorageRoot().resolve("bm25") : configuredPath;
            Files.createDirectories(indexPath);
            directory = FSDirectory.open(indexPath);
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());
            writer = new IndexWriter(directory, config);
            log.info("[KnowledgeSearch][BM25] initialized indexPath={} enabled={}", indexPath, enabled);
        } catch (IOException ex) {
            throw new IllegalStateException("无法初始化知识库 BM25 索引", ex);
        }
    }

    @Override
    public synchronized void replaceDocument(String knowledgeBaseUid, String documentUid, String documentVersionUid,
                                             String documentName, List<IndexedChunk> chunks) {
        if (!enabled) return;
        try {
            writer.deleteDocuments(new Term(FIELD_DOCUMENT_UID, documentUid));
            for (IndexedChunk chunk : chunks) {
                Document document = new Document();
                document.add(new StringField(FIELD_CHUNK_UID, chunk.chunkUid(), Field.Store.YES));
                document.add(new StringField(FIELD_KNOWLEDGE_BASE_UID, knowledgeBaseUid, Field.Store.NO));
                document.add(new StringField(FIELD_DOCUMENT_UID, documentUid, Field.Store.NO));
                document.add(new StringField(FIELD_DOCUMENT_VERSION_UID, documentVersionUid, Field.Store.NO));
                document.add(new TextField(FIELD_DOCUMENT_NAME, safe(documentName), Field.Store.NO));
                document.add(new TextField(FIELD_SECTION_PATH, safe(chunk.sectionPath()), Field.Store.NO));
                document.add(new TextField(FIELD_CONTENT, safe(chunk.content()), Field.Store.NO));
                writer.addDocument(document);
            }
            writer.commit();
            log.info("[KnowledgeSearch][BM25] indexed knowledgeBaseUid={} documentUid={} chunkCount={}",
                    knowledgeBaseUid, documentUid, chunks.size());
        } catch (IOException ex) {
            throw new IllegalStateException("写入知识库 BM25 索引失败", ex);
        }
    }

    @Override
    public synchronized List<Hit> search(String query, List<String> knowledgeBaseUids, int limit) {
        if (!available() || query == null || query.isBlank() || knowledgeBaseUids == null || knowledgeBaseUids.isEmpty()) return List.of();
        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            Query textQuery = textQuery(query);
            Query baseFilter = new TermInSetQuery(FIELD_KNOWLEDGE_BASE_UID,
                    knowledgeBaseUids.stream().map(BytesRef::new).toList());
            BooleanQuery combined = new BooleanQuery.Builder()
                    .add(textQuery, BooleanClause.Occur.MUST)
                    .add(baseFilter, BooleanClause.Occur.FILTER)
                    .build();
            TopDocs results = searcher.search(combined, limit);
            List<Hit> hits = new java.util.ArrayList<>(results.scoreDocs.length);
            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document document = searcher.doc(scoreDoc.doc);
                hits.add(new Hit(document.get(FIELD_CHUNK_UID), scoreDoc.score));
            }
            log.info("[KnowledgeSearch][BM25] knowledgeBaseCount={} limit={} candidateCount={} scores={}",
                    knowledgeBaseUids.size(), limit, hits.size(), hits.stream().map(Hit::score).toList());
            return hits;
        } catch (Exception ex) {
            throw new IllegalStateException("查询知识库 BM25 索引失败", ex);
        }
    }

    @Override
    public synchronized void deleteByDocument(String documentUid) {
        delete(new Term(FIELD_DOCUMENT_UID, documentUid));
    }

    @Override
    public synchronized void deleteByKnowledgeBase(String knowledgeBaseUid) {
        delete(new Term(FIELD_KNOWLEDGE_BASE_UID, knowledgeBaseUid));
    }

    @Override
    public synchronized void clear() {
        if (!enabled) return;
        try {
            writer.deleteAll();
            writer.commit();
        } catch (IOException ex) {
            throw new IllegalStateException("清理知识库 BM25 索引失败", ex);
        }
    }

    @Override
    public boolean available() {
        return enabled && writer.isOpen();
    }

    /**
     * Closes local Lucene resources when the application stops.
     */
    @PreDestroy
    public synchronized void close() {
        try {
            writer.close();
            directory.close();
            analyzer.close();
        } catch (IOException ex) {
            log.warn("Unable to close knowledge BM25 index", ex);
        }
    }

    private Query textQuery(String query) throws Exception {
        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{FIELD_DOCUMENT_NAME, FIELD_SECTION_PATH, FIELD_CONTENT}, analyzer,
                Map.of(FIELD_DOCUMENT_NAME, 3.0F, FIELD_SECTION_PATH, 2.0F, FIELD_CONTENT, 1.0F));
        return parser.parse(QueryParser.escape(query));
    }

    private void delete(Term term) {
        if (!enabled) return;
        try {
            writer.deleteDocuments(term);
            writer.commit();
        } catch (IOException ex) {
            throw new IllegalStateException("删除知识库 BM25 索引失败", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
