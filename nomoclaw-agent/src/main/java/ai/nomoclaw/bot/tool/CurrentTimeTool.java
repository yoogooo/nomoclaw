package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class CurrentTimeTool implements Tool {

    @Override
    public String name() {
        return "CurrentTimeTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String text = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC' (EEEE)", Locale.ENGLISH));
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("utc", now.toInstant().toString());
        artifacts.put("formatted", text);
        return ToolResult.success(text, artifacts, metrics());
    }

    private ObjectNode metrics() {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }
}
