package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.api.dto.common.response.PageResponse;
import ai.nomoclaw.bot.api.dto.system.response.TokenUsageBreakdownResponse;
import ai.nomoclaw.bot.api.dto.system.response.TokenUsageOverviewResponse;
import ai.nomoclaw.bot.api.dto.system.response.TokenUsageRecordResponse;
import ai.nomoclaw.bot.store.entity.TokenUsageRecordEntity;
import ai.nomoclaw.bot.store.repository.TokenUsageRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TokenUsageQueryService {
    private final TokenUsageRecordRepository repository;

    public TokenUsageQueryService(TokenUsageRecordRepository repository) { this.repository = repository; }

    public TokenUsageOverviewResponse overview(LocalDateTime from, LocalDateTime to, String provider, String model, String scene) {
        List<TokenUsageRecordEntity> records = find(from, to, provider, model, scene);
        return new TokenUsageOverviewResponse(sum(records, TokenUsageRecordEntity::getInputTokens),
                sum(records, TokenUsageRecordEntity::getCachedInputTokens), sum(records, TokenUsageRecordEntity::getOutputTokens),
                sum(records, TokenUsageRecordEntity::getTotalTokens), records.size(),
                records.stream().filter(item -> !Boolean.TRUE.equals(item.getUsageAvailable())).count(),
                breakdown(records, item -> item.getOccurredTime() == null ? "unknown" : item.getOccurredTime().toLocalDate().toString()),
                breakdown(records, item -> value(item.getProvider()) + "/" + value(item.getModelName())),
                breakdown(records, item -> displayScene(item.getScene())));
    }

    public PageResponse<TokenUsageRecordResponse> page(LocalDateTime from, LocalDateTime to, String provider, String model, String scene, int page, int pageSize) {
        List<TokenUsageRecordEntity> records = find(from, to, provider, model, scene);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.clamp(pageSize, 1, 100);
        int fromIndex = Math.min(records.size(), (normalizedPage - 1) * normalizedSize);
        int toIndex = Math.min(records.size(), fromIndex + normalizedSize);
        List<TokenUsageRecordResponse> items = records.subList(fromIndex, toIndex).stream().map(this::toResponse).toList();
        return new PageResponse<>(items, records.size(), normalizedPage, normalizedSize,
                (records.size() + normalizedSize - 1L) / normalizedSize);
    }

    private List<TokenUsageRecordEntity> find(LocalDateTime from, LocalDateTime to, String provider, String model, String scene) {
        return repository.listByFilter(from, to, provider, model, scene);
    }

    private List<TokenUsageBreakdownResponse> breakdown(List<TokenUsageRecordEntity> records, Function<TokenUsageRecordEntity, String> classifier) {
        return records.stream().collect(Collectors.groupingBy(classifier)).entrySet().stream()
                .map(entry -> new TokenUsageBreakdownResponse(entry.getKey(), sum(entry.getValue(), TokenUsageRecordEntity::getTotalTokens), entry.getValue().size()))
                .sorted(Comparator.comparing(TokenUsageBreakdownResponse::totalTokens).reversed()).toList();
    }

    private long sum(List<TokenUsageRecordEntity> records, Function<TokenUsageRecordEntity, Integer> getter) {
        return records.stream().map(getter).mapToLong(value -> value == null ? 0 : value).sum();
    }
    private TokenUsageRecordResponse toResponse(TokenUsageRecordEntity item) {
        return new TokenUsageRecordResponse(value(item.getRecordUid()), value(item.getScene()), value(item.getProvider()), value(item.getModelName()),
                value(item.getConversationUid()), value(item.getMessageUid()), number(item.getInputTokens()), number(item.getCachedInputTokens()),
                number(item.getOutputTokens()), number(item.getReasoningTokens()), number(item.getTotalTokens()),
                Boolean.TRUE.equals(item.getUsageAvailable()), item.getOccurredTime());
    }
    private String displayScene(String scene) {
        return "CHAT_REASONING".equals(scene) || "CHAT_SUMMARY".equals(scene) ? "CHAT" : value(scene);
    }
    private String value(String value) { return value == null ? "" : value; }
    private int number(Integer value) { return value == null ? 0 : value; }
}
