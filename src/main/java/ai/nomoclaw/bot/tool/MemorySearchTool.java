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
import java.util.List;

@Component
public class MemorySearchTool implements Tool {

    private final AgentMessageRepository messageRepository;

    public MemorySearchTool(AgentMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public String name() {
        return "MemorySearchTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        String query = request.args().path("query").asString("");
        int maxResults = Math.clamp(request.args().path("maxResults").asInt(5), 1, 20);
        if (query.isBlank()) {
            return ToolResult.failure("INVALID_ARGS", "query is required", metrics(start));
        }

        List<AgentMessageEntity> matched = messageRepository.lambdaQuery()
                .like(AgentMessageEntity::getContent, query)
                .orderByDesc(AgentMessageEntity::getId)
                .last("LIMIT " + maxResults)
                .list();

        ArrayNode items = JsonNodeFactory.instance.arrayNode();
        StringBuilder text = new StringBuilder();
        for (AgentMessageEntity item : matched) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("messageUid", item.getMessageUid());
            node.put("conversationUid", item.getConversationUid());
            node.put("role", item.getRole());
            node.put("content", item.getContent());
            items.add(node);
            text.append('[').append(item.getRole()).append("] ")
                    .append(ToolTextUtils.truncateHead(item.getContent()))
                    .append("\n\n");
        }

        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.set("matches", items);
        artifacts.put("count", matched.size());
        return ToolResult.success(text.toString().trim(), artifacts, metrics(start));
    }

    private ObjectNode metrics(long start) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
