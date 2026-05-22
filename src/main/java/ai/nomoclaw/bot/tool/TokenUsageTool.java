package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.store.entity.AgentMessageEntity;
import ai.nomoclaw.bot.store.repository.AgentMessageRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TokenUsageTool implements Tool {

    private final AgentMessageRepository messageRepository;

    public TokenUsageTool(AgentMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public String name() {
        return "TokenUsageTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        int days = Math.clamp(request.args().path("days").asInt(30), 1, 365);
        String modelName = request.args().path("modelName").asString("");
        String provider = request.args().path("provider").asString("");
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<AgentMessageEntity> messages = messageRepository.lambdaQuery()
                .ge(AgentMessageEntity::getCreatedTime, since)
                .list();

        if (!modelName.isBlank()) {
            messages = messages.stream().filter(it -> modelName.equals(it.getModelName())).toList();
        }
        if (!provider.isBlank()) {
            messages = messages.stream().filter(it -> provider.equals(it.getProvider())).toList();
        }

        int input = messages.stream().mapToInt(it -> safeInt(it.getInputTokens())).sum();
        int cachedInput = messages.stream().mapToInt(it -> safeInt(it.getCachedInputTokens())).sum();
        int output = messages.stream().mapToInt(it -> safeInt(it.getOutputTokens())).sum();
        int total = messages.stream().mapToInt(it -> safeInt(it.getTotalTokens())).sum();

        Map<String, Integer> byModel = messages.stream()
                .collect(Collectors.groupingBy(
                        it -> it.getModelName() == null || it.getModelName().isBlank() ? "unknown" : it.getModelName(),
                        Collectors.summingInt(it -> safeInt(it.getTotalTokens()))
                ));

        ArrayNode modelStats = JsonNodeFactory.instance.arrayNode();
        byModel.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> {
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put("modelName", entry.getKey());
                    node.put("totalTokens", entry.getValue());
                    modelStats.add(node);
                });

        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("days", days);
        artifacts.put("inputTokens", input);
        artifacts.put("cachedInputTokens", cachedInput);
        artifacts.put("outputTokens", output);
        artifacts.put("totalTokens", total);
        artifacts.put("messageCount", messages.size());
        artifacts.put("cachedRatio", input <= 0 ? 0D : (double) cachedInput / (double) input);
        artifacts.set("byModel", modelStats);

        String outputText = """
                Token usage summary:
                - days: %d
                - total tokens: %d
                - input tokens: %d
                - cached input tokens: %d
                - output tokens: %d
                - messages: %d
                """.formatted(days, total, input, cachedInput, output, messages.size()).trim();
        return ToolResult.success(outputText, artifacts, metrics(start));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private ObjectNode metrics(long start) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
