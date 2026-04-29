package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.store.entity.SystemErrorLogEntity;
import ai.nomoclaw.bot.store.repository.SystemErrorLogRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SystemErrorLogServiceTests {

    @Test
    void listLatestCapsLimitAtTwoHundred() {
        FakeSystemErrorLogRepository repository = new FakeSystemErrorLogRepository();
        SystemErrorLogService service = new SystemErrorLogService(repository);

        service.listLatest(500);

        assertEquals(200, repository.lastListLimit);
    }

    @Test
    void summaryReturnsRecentCountAndLatestTime() {
        FakeSystemErrorLogRepository repository = new FakeSystemErrorLogRepository();
        LocalDateTime occurredTime = LocalDateTime.of(2026, 4, 28, 15, 16, 49);
        SystemErrorLogEntity latest = new SystemErrorLogEntity();
        latest.setOccurredTime(occurredTime);
        repository.latest = latest;
        repository.countSinceResult = 2L;
        SystemErrorLogService service = new SystemErrorLogService(repository);

        var summary = service.summary();

        assertEquals(true, summary.hasErrors());
        assertEquals(2L, summary.recent24hCount());
        assertEquals(occurredTime, summary.latestOccurredTime());
    }

    @Test
    void recordPersistsErrorAndTrimsRetention() {
        FakeSystemErrorLogRepository repository = new FakeSystemErrorLogRepository();
        SystemErrorLogService service = new SystemErrorLogService(repository);

        service.record("WARN", "Quartz", "QUARTZ_RESTORE_FAILED", "Quartz 恢复任务失败", "restore failed", "stack");

        assertEquals(1000, repository.lastTrimKeepCount);
        SystemErrorLogEntity entity = repository.saved.getFirst();
        assertNotNull(entity.getLogUid());
        assertEquals("WARN", entity.getLevel());
        assertEquals("Quartz", entity.getSource());
        assertEquals("QUARTZ_RESTORE_FAILED", entity.getCode());
        assertEquals("restore failed", entity.getMessage());
        assertEquals("stack", entity.getDetail());
        assertNotNull(entity.getOccurredTime());
        assertNotNull(entity.getCreatedTime());
    }

    @Test
    void recordSwallowsPersistenceFailure() {
        FakeSystemErrorLogRepository repository = new FakeSystemErrorLogRepository();
        repository.throwOnSave = true;
        SystemErrorLogService service = new SystemErrorLogService(repository);

        assertDoesNotThrow(() -> service.record("ERROR", "Flyway", "FLYWAY_MIGRATE_FAILED", "Flyway 迁移失败", "failed", "stack"));
    }

    private static class FakeSystemErrorLogRepository extends SystemErrorLogRepository {
        private final List<SystemErrorLogEntity> saved = new ArrayList<>();
        private int lastListLimit;
        private int lastTrimKeepCount;
        private long countSinceResult;
        private boolean throwOnSave;
        private SystemErrorLogEntity latest;

        @Override
        public List<SystemErrorLogEntity> listLatest(int limit) {
            lastListLimit = limit;
            return List.of();
        }

        @Override
        public SystemErrorLogEntity latest() {
            return latest;
        }

        @Override
        public long countSince(LocalDateTime since) {
            return countSinceResult;
        }

        @Override
        public boolean save(SystemErrorLogEntity entity) {
            if (throwOnSave) {
                throw new IllegalStateException("database unavailable");
            }
            saved.add(entity);
            return true;
        }

        @Override
        public void trimToLatest(int keepCount) {
            lastTrimKeepCount = keepCount;
        }
    }
}
