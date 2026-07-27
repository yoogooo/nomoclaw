package ai.nomoclaw.bot.knowledge.config;

import ai.nomoclaw.bot.knowledge.bm25.LexicalSearchStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25IndexBootstrapTest {
    @Test
    void skipsStartupRebuildWhenDisabled() {
        KnowledgeProperties properties = KnowledgePropertiesTestSupport.properties();
        properties.getRetrieval().getBm25().setStartupRebuildEnabled(false);
        RecordingLexicalSearchStore lexicalSearchStore = new RecordingLexicalSearchStore();
        Bm25IndexBootstrap bootstrap = new Bm25IndexBootstrap(lexicalSearchStore,
                null, null, null, properties, Runnable::run);

        bootstrap.run(new DefaultApplicationArguments());

        assertThat(lexicalSearchStore.clearCount).isZero();
    }

    private static final class RecordingLexicalSearchStore implements LexicalSearchStore {
        private int clearCount;

        @Override
        public void replaceDocument(String knowledgeBaseUid, String documentUid, String documentVersionUid,
                                    String documentName, List<IndexedChunk> chunks) {
        }

        @Override
        public List<Hit> search(String query, List<String> knowledgeBaseUids, int limit) {
            return List.of();
        }

        @Override
        public void deleteByDocument(String documentUid) {
        }

        @Override
        public void deleteByDocumentVersion(String documentVersionUid) {
        }

        @Override
        public void deleteByKnowledgeBase(String knowledgeBaseUid) {
        }

        @Override
        public void clear() {
            clearCount++;
        }

        @Override
        public boolean available() {
            return true;
        }
    }

}
