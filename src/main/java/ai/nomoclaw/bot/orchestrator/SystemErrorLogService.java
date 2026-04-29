package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.SystemErrorLogDto;
import ai.nomoclaw.bot.application.dto.SystemErrorLogSummaryDto;
import ai.nomoclaw.bot.store.entity.SystemErrorLogEntity;
import ai.nomoclaw.bot.store.repository.SystemErrorLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class SystemErrorLogService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;
    private static final int RETENTION_COUNT = 1000;
    private static final int MAX_TEXT_LENGTH = 8000;

    private final SystemErrorLogRepository repository;

    public SystemErrorLogService(SystemErrorLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SystemErrorLogDto> listLatest(Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        return repository.listLatest(limit).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SystemErrorLogSummaryDto summary() {
        SystemErrorLogEntity latest = repository.latest();
        long recent24hCount = repository.countSince(LocalDateTime.now().minusHours(24));
        return new SystemErrorLogSummaryDto(
                latest != null,
                recent24hCount,
                latest == null ? null : latest.getOccurredTime()
        );
    }

    public void record(String level, String source, String code, String title, String message, String detail) {
        try {
            recordInternal(level, source, code, title, message, detail);
        } catch (Exception ex) {
            log.warn("[SystemErrorLog] failed to persist source={} code={} err={}", source, code, ex.toString());
        }
    }

    public void recordException(String level, String source, String code, String title, String message, Throwable throwable) {
        record(level, source, code, title, message, stackTraceSummary(throwable));
    }

    protected void recordInternal(String level, String source, String code, String title, String message, String detail) {
        LocalDateTime now = LocalDateTime.now();
        SystemErrorLogEntity entity = new SystemErrorLogEntity();
        entity.setLogUid(UUID.randomUUID().toString());
        entity.setLevel(normalizeLevel(level));
        entity.setSource(truncate(source, 64));
        entity.setCode(truncate(code, 128));
        entity.setTitle(truncate(title, 255));
        entity.setMessage(truncate(message, MAX_TEXT_LENGTH));
        entity.setDetail(truncate(detail, MAX_TEXT_LENGTH));
        entity.setOccurredTime(now);
        entity.setCreatedTime(now);
        repository.save(entity);
        repository.trimToLatest(RETENTION_COUNT);
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private String normalizeLevel(String level) {
        String normalized = nullToEmpty(level).trim().toUpperCase(Locale.ROOT);
        return "WARN".equals(normalized) ? "WARN" : "ERROR";
    }

    private SystemErrorLogDto toDto(SystemErrorLogEntity entity) {
        return new SystemErrorLogDto(
                entity.getLogUid(),
                entity.getLevel(),
                entity.getSource(),
                entity.getCode(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getDetail(),
                entity.getOccurredTime()
        );
    }

    private String stackTraceSummary(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return truncate(writer.toString(), MAX_TEXT_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        String text = nullToEmpty(value).trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
