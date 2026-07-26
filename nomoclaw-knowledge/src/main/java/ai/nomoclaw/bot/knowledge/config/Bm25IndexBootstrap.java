package ai.nomoclaw.bot.knowledge.config;

import ai.nomoclaw.bot.knowledge.bm25.LexicalSearchStore;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeBaseEntity;
import ai.nomoclaw.bot.knowledge.core.entity.KnowledgeDocumentEntity;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeBaseRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeChunkRepository;
import ai.nomoclaw.bot.knowledge.core.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Rebuilds the derived BM25 index from current ready document versions after application startup.
 */
@Component
public class Bm25IndexBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Bm25IndexBootstrap.class);

    private final LexicalSearchStore lexicalSearchStore;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeProperties properties;
    private final Executor executor;

    public Bm25IndexBootstrap(LexicalSearchStore lexicalSearchStore, KnowledgeBaseRepository knowledgeBaseRepository,
                              KnowledgeDocumentRepository documentRepository, KnowledgeChunkRepository chunkRepository,
                              KnowledgeProperties properties, @Qualifier("knowledgeIngestionExecutor") Executor executor) {
        this.lexicalSearchStore = lexicalSearchStore;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getRetrieval().getBm25().isStartupRebuildEnabled()) {
            log.info("[KnowledgeSearch][BM25] startup rebuild skipped");
            return;
        }
        if (!lexicalSearchStore.available()) return;
        executor.execute(this::rebuild);
    }

    private void rebuild() {
        try {
            lexicalSearchStore.clear();
            int documentCount = 0;
            for (KnowledgeBaseEntity base : knowledgeBaseRepository.listVisible("")) {
                for (KnowledgeDocumentEntity document : documentRepository.listByBase(base.getKnowledgeBaseUid())) {
                    if (!"READY".equals(document.getStatus()) || document.getCurrentVersionUid() == null
                            || document.getCurrentVersionUid().isBlank()) continue;
                    lexicalSearchStore.replaceDocument(base.getKnowledgeBaseUid(), document.getDocumentUid(),
                            document.getCurrentVersionUid(), document.getDisplayName(), chunkRepository
                                    .listReadyByDocumentVersion(document.getDocumentUid(), document.getCurrentVersionUid())
                                    .stream()
                                    .map(chunk -> new LexicalSearchStore.IndexedChunk(chunk.getChunkUid(), chunk.getSectionPath(), chunk.getContent()))
                                    .toList());
                    documentCount++;
                }
            }
            log.info("[KnowledgeSearch][BM25] startup rebuild completed documentCount={}", documentCount);
        } catch (Exception ex) {
            log.warn("[KnowledgeSearch][BM25] startup rebuild failed; dense retrieval remains available", ex);
        }
    }
}
