package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentToolRelationEntity;
import ai.nomoclaw.bot.store.entity.McpToolSnapshotEntity;
import ai.nomoclaw.bot.store.entity.ToolDefinitionEntity;
import ai.nomoclaw.bot.mcp.McpApplicationService;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentToolRelationRepository;
import ai.nomoclaw.bot.store.repository.ToolDefinitionRepository;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ToolSpecificationRegistry {

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolSpecification> toolSpecificationsByName;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final AgentToolRelationRepository agentToolRelationRepository;
    private final McpApplicationService mcpApplicationService;

    public ToolSpecificationRegistry(AgentDefinitionRepository agentDefinitionRepository,
                                     ToolDefinitionRepository toolDefinitionRepository,
                                     AgentToolRelationRepository agentToolRelationRepository,
                                     McpApplicationService mcpApplicationService) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.agentToolRelationRepository = agentToolRelationRepository;
        this.mcpApplicationService = mcpApplicationService;
        this.toolSpecifications = List.of(
                ToolSpecification.builder()
                        .name("CommandTool")
                        .description("Execute a local shell command on the current machine. Relative cwd values are resolved from the current agent workspace.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Command execution arguments")
                                .addStringProperty("command", "The shell command to execute")
                                .addStringProperty("cwd", "Working directory for the command. Relative paths are resolved from the current agent workspace. Omit to use the agent workspace root.")
                                .required("command")
                                .additionalProperties(true)
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("BrowserTool")
                        .description("Operate a browser page to open URLs, click, type, extract text, or take screenshots. Screenshot and download output paths default to the current agent tmp directory.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Browser action arguments")
                                .addEnumProperty("action", List.of("open", "navigate", "navigate_back", "click", "type", "extract_text", "screenshot", "download", "snapshot", "wait_for", "press_key", "close"), "Browser action name")
                                .addStringProperty("url", "URL for open or navigate")
                                .addStringProperty("selector", "Target selector")
                                .addStringProperty("text", "Input text for action=type")
                                .addStringProperty("output", "Output path or directory for screenshot/download. Relative paths are resolved from the current agent workspace. Omit to save into tmp/.")
                                .addStringProperty("key", "Keyboard key")
                                .required("action")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("ReadFileTool")
                        .description("Read local file content by path with optional line range. Relative paths are resolved from the current agent workspace.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Read file arguments")
                                .addStringProperty("path", "File path")
                                .addIntegerProperty("startLine", "Start line for read")
                                .addIntegerProperty("endLine", "End line for read")
                                .required("path")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("ListFileTool")
                        .description("List direct children of a local directory by path. Relative paths are resolved from the current agent workspace.")
                        .parameters(JsonObjectSchema.builder()
                                .description("List file arguments")
                                .addStringProperty("path", "Directory path")
                                .required("path")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("CreateFileTool")
                        .description("Create or append local file content. Relative paths are resolved from the current agent workspace; final deliverables should be written under report/.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Create file arguments")
                                .addStringProperty("path", "File path")
                                .addEnumProperty("mode", List.of("create_or_truncate", "append"), "Write mode")
                                .addStringProperty("content", "Content to write")
                                .required("path", "content")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("EditFileTool")
                        .description("Edit local file by replacing oldText with newText. Relative paths are resolved from the current agent workspace.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Edit file arguments")
                                .addStringProperty("path", "File path")
                                .addStringProperty("oldText", "Old text for edit")
                                .addStringProperty("newText", "New text for edit")
                                .required("path", "oldText")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("WebSearchTool")
                        .description("Search the public web for recent information by query, with optional allowed or blocked domain filters.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Web search arguments")
                                .addStringProperty("query", "Search query text")
                                .addStringProperty("allowed_domains", "Optional domain whitelist, for example [\"example.com\"]. Cannot be used with blocked_domains.")
                                .addStringProperty("blocked_domains", "Optional domain blacklist, for example [\"example.com\"]. Cannot be used with allowed_domains.")
                                .required("query")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("WebFetchTool")
                        .description("Fetch a public HTTP(S) URL and return extracted text content.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Web fetch arguments")
                                .addStringProperty("url", "HTTP(S) URL")
                                .addStringProperty("prompt", "Optional processing prompt context")
                                .required("url")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("CronCreateTool")
                        .description("Create a persisted scheduled task using Quartz cron syntax. Use 6 fields for recurring schedules and 7 fields with an explicit year for a one-time schedule, for example `0 30 19 26 4 ? 2026`; never drop the year when the user asks to run only once. Relative-time requests such as 'in 5 minutes' must be calculated strictly from the current local time and local timezone provided in the environment context. Do not execute the task immediately unless the user explicitly asks for both scheduling and immediate execution. Each run will execute the task with the current agent, write a markdown report under report/cron, and invoke the notification interface. To modify an existing scheduled task, first use CronListTool to find the old jobUid, then CronDeleteTool, then CronCreateTool with the replacement schedule.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Cron create arguments")
                                .addStringProperty("title", "Short task title for display in the management UI. Omit to derive a title from task.")
                                .addStringProperty("expression", "A valid Quartz cron expression. Use 6 fields for recurring schedules, for example `0 0 6 ? * *`; use 7 fields with year for one-time schedules, for example `0 30 19 26 4 ? 2026`.")
                                .addStringProperty("timezone", "IANA timezone, such as Asia/Shanghai")
                                .addStringProperty("task", "The task to execute when the schedule triggers. Do not use this tool if the user only wants immediate execution.")
                                .required("expression", "timezone", "task")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("CronDeleteTool")
                        .description("Delete a persisted scheduled task by jobUid. Use CronListTool first if the user describes the task by title, schedule, or content instead of an exact jobUid. For modifications, delete the old job and then create the replacement with CronCreateTool.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Cron delete arguments")
                                .addStringProperty("jobUid", "Cron job UID to delete. Obtain it from CronListTool when needed.")
                                .required("jobUid")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("CronListTool")
                        .description("List persisted scheduled tasks, or query one task by jobUid. Use this before deleting or modifying a scheduled task so the correct jobUid and existing schedule are known.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Cron list/query arguments")
                                .addStringProperty("jobUid", "Optional cron job UID. When provided, returns that single job.")
                                .addStringProperty("status", "Optional status filter, such as ACTIVE or PAUSED, used only when jobUid is omitted.")
                                .addIntegerProperty("limit", "Maximum jobs to return when jobUid is omitted. Defaults to 50 and caps at 100.")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("FileSearchTool")
                        .description("Search files by text grep or glob pattern. Relative paths and default search roots use the current agent workspace.")
                        .parameters(JsonObjectSchema.builder()
                                .description("File search arguments")
                                .addEnumProperty("action", List.of("grep", "glob"), "Search action")
                                .addStringProperty("query", "Text query for grep")
                                .addStringProperty("pattern", "Glob pattern for glob action")
                                .addStringProperty("path", "Root path for search. Relative paths are resolved from the current agent workspace. Omit to search the agent workspace root.")
                                .required("action")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("GrepTool")
                        .description("Search file contents with regular expressions under the current agent workspace.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Grep arguments")
                                .addStringProperty("pattern", "Regular expression pattern")
                                .addStringProperty("path", "Optional root file/directory. Relative paths are resolved from the current agent workspace.")
                                .addStringProperty("glob", "Optional glob filter, for example *.java or **/*.md")
                                .addEnumProperty("output_mode", List.of("content", "files_with_matches", "count"), "Output mode. Defaults to files_with_matches.")
                                .addBooleanProperty("-i", "Case insensitive search")
                                .addBooleanProperty("-n", "Show line numbers in content output. Defaults to true.")
                                .addIntegerProperty("head_limit", "Maximum returned lines/entries. Defaults to 250. Use 0 for unlimited.")
                                .addIntegerProperty("offset", "Skip first N lines/entries before applying head_limit.")
                                .required("pattern")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("DesktopScreenshotTool")
                        .description("Capture a desktop screenshot on the local machine. Output defaults to the current agent tmp directory.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Desktop screenshot arguments")
                                .addStringProperty("path", "Output image path. Relative paths are resolved from the current agent workspace. Omit to save into tmp/.")
                                .addBooleanProperty("captureWindow", "Whether to capture a selected window")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("ImageLoaderTool")
                        .description("按需加载图片上下文，仅在需要视觉分析时使用。适用于截图、浏览器等返回图片路径的工具步骤之后；支持“刚才截图”“第 N 轮截图”“文件名”引用，以及直接传入 HTTP(S) 图片链接（仅透传，不本地下载）。")
                        .parameters(JsonObjectSchema.builder()
                                .description("图片加载参数")
                                .addStringProperty("reference", "图片引用，示例：刚才截图、第 3 轮截图、desktop_screenshot_xxx.png、https://example.com/demo.png")
                                .addIntegerProperty("maxImages", "最多解析图片数量，范围 1-3，默认 1。")
                                .required("reference")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("CurrentTimeTool")
                        .description("Get the current UTC time.")
                        .parameters(JsonObjectSchema.builder()
                                .description("No arguments required")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("TokenUsageTool")
                        .description("Query stored token usage summary from message records.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Token usage query arguments")
                                .addIntegerProperty("days", "Days to look back")
                                .addStringProperty("modelName", "Optional model name filter")
                                .addStringProperty("provider", "Optional provider filter")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("MemorySearchTool")
                        .description("Search historical conversation messages by keyword.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Memory search arguments")
                                .addStringProperty("query", "Search query")
                                .addIntegerProperty("maxResults", "Maximum results")
                                .required("query")
                                .additionalProperties(true)
                                .build())
                        .build()
        );
        this.toolSpecificationsByName = toolSpecifications.stream()
                .collect(Collectors.toMap(ToolSpecification::name, specification -> specification, (left, right) -> left, LinkedHashMap::new));
    }

    public List<ToolSpecification> listAll() {
        return allToolSpecificationsByName().values().stream().toList();
    }

    public List<ToolSpecification> listForAgent(String agentName) {
        List<String> enabledBuiltinToolKeys = enabledBuiltinToolKeys(agentName);
        if (enabledBuiltinToolKeys == null) {
            return listAll();
        }
        Map<String, ToolSpecification> allTools = allToolSpecificationsByName();
        List<String> enabledToolKeys = new ArrayList<>(enabledBuiltinToolKeys);
        enabledToolKeys.addAll(enabledMcpToolKeys(agentName));
        return enabledToolKeys.stream()
                .map(allTools::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean isToolAllowed(String agentName, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String normalizedToolName = toolName.trim();
        List<String> enabledBuiltinToolKeys = enabledBuiltinToolKeys(agentName);
        if (enabledBuiltinToolKeys == null) {
            return allToolSpecificationsByName().containsKey(normalizedToolName);
        }
        return enabledBuiltinToolKeys.contains(normalizedToolName) || enabledMcpToolKeys(agentName).contains(normalizedToolName);
    }

    public boolean isMcpTool(String toolName) {
        return mcpApplicationService.isMcpTool(toolName);
    }

    private List<String> enabledBuiltinToolKeys(String agentName) {
        AgentDefinitionEntity agent = agentDefinitionRepository.findActiveByName(agentName);
        if (agent == null) {
            return null;
        }

        List<AgentToolRelationEntity> relations = agentToolRelationRepository.listActiveByAgentUid(agent.getAgentUid());
        if (relations.isEmpty()) {
            // No active relation means "no tools enabled" for this agent.
            return List.of();
        }

        Set<String> knownKeys = toolSpecificationsByName.keySet();
        List<String> relationToolKeys = relations.stream()
                .map(AgentToolRelationEntity::getToolKey)
                .toList();
        Set<String> activeDefinitionKeys = toolDefinitionRepository.listActiveByKeys(relationToolKeys)
                .stream()
                .map(definition -> definition.getToolKey())
                .collect(Collectors.toSet());

        return relationToolKeys.stream()
                .filter(activeDefinitionKeys::contains)
                .filter(knownKeys::contains)
                .distinct()
                .toList();
    }

    private List<String> enabledMcpToolKeys(String agentName) {
        AgentDefinitionEntity agent = agentDefinitionRepository.findActiveByName(agentName);
        if (agent == null) {
            return List.of();
        }
        return mcpApplicationService.listActiveToolSnapshotsForAgent(agent.getAgentUid()).stream()
                .map(McpToolSnapshotEntity::getToolKey)
                .distinct()
                .toList();
    }

    private Map<String, ToolSpecification> allToolSpecificationsByName() {
        Map<String, ToolSpecification> all = new LinkedHashMap<>(toolSpecificationsByName);
        for (McpToolSnapshotEntity snapshot : mcpApplicationService.listActiveToolSnapshots()) {
            all.put(snapshot.getToolKey(), toMcpToolSpecification(snapshot));
        }
        return all;
    }

    private ToolSpecification toMcpToolSpecification(McpToolSnapshotEntity snapshot) {
        String description = buildMcpToolDescription(snapshot);
        return ToolSpecification.builder()
                .name(snapshot.getToolKey())
                .description(description)
                .parameters(toMcpInputSchema(snapshot))
                .build();
    }

    private JsonObjectSchema toMcpInputSchema(McpToolSnapshotEntity snapshot) {
        String schema = snapshot.getInputSchemaJson() == null ? "" : snapshot.getInputSchemaJson().trim();
        if (schema.isBlank()) {
            return JsonObjectSchema.builder()
                    .description("Arguments for MCP tool " + snapshot.getOriginalToolName())
                    .additionalProperties(true)
                    .build();
        }
        try {
            JsonNode root = JsonUtil.fromJson(schema, JsonNode.class);
            return toObjectSchema(root, "Arguments for MCP tool " + snapshot.getOriginalToolName());
        } catch (Exception ignored) {
            return JsonObjectSchema.builder()
                    .description("Arguments for MCP tool " + snapshot.getOriginalToolName())
                    .additionalProperties(true)
                    .build();
        }
    }

    private JsonObjectSchema toObjectSchema(JsonNode node, String fallbackDescription) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder()
                .description(firstNonBlank(text(node.path("description")), fallbackDescription));
        JsonNode properties = node.path("properties");
        if (properties.isObject()) {
            properties.properties().forEach(entry -> builder.addProperty(entry.getKey(), toSchemaElement(entry.getValue())));
        }
        JsonNode required = node.path("required");
        if (required.isArray()) {
            List<String> requiredFields = new ArrayList<>();
            required.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    requiredFields.add(value);
                }
            });
            if (!requiredFields.isEmpty()) {
                builder.required(requiredFields);
            }
        }
        if (node.path("additionalProperties").isBoolean()) {
            builder.additionalProperties(node.path("additionalProperties").asBoolean());
        } else {
            builder.additionalProperties(false);
        }
        return builder.build();
    }

    private JsonSchemaElement toSchemaElement(JsonNode node) {
        JsonNode enumNode = node.path("enum");
        if (enumNode.isArray() && !enumNode.isEmpty()) {
            List<String> values = new ArrayList<>();
            enumNode.forEach(item -> values.add(item.asText("")));
            return JsonEnumSchema.builder()
                    .description(text(node.path("description")))
                    .enumValues(values)
                    .build();
        }
        String type = text(node.path("type")).toLowerCase();
        String description = text(node.path("description"));
        return switch (type) {
            case "integer" -> JsonIntegerSchema.builder().description(description).build();
            case "number" -> JsonNumberSchema.builder().description(description).build();
            case "boolean" -> JsonBooleanSchema.builder().description(description).build();
            case "array" -> JsonArraySchema.builder()
                    .description(description)
                    .items(toSchemaElement(node.path("items")))
                    .build();
            case "object" -> toObjectSchema(node, description);
            default -> JsonStringSchema.builder().description(description).build();
        };
    }

    private String buildMcpToolDescription(McpToolSnapshotEntity snapshot) {
        String base = snapshot.getDescription() == null ? "" : snapshot.getDescription().trim();
        String schema = snapshot.getInputSchemaJson() == null ? "" : snapshot.getInputSchemaJson().trim();
        if (schema.isBlank()) {
            return base;
        }
        try {
            JsonNode root = JsonUtil.fromJson(schema, JsonNode.class);
            JsonNode properties = root == null ? null : root.path("properties");
            if (properties == null || !properties.isObject() || properties.isEmpty()) {
                return base;
            }
            Set<String> required = new HashSet<>();
            JsonNode requiredNode = root.path("required");
            if (requiredNode.isArray()) {
                requiredNode.forEach(item -> {
                    String name = item.asText("").trim();
                    if (!name.isBlank()) {
                        required.add(name);
                    }
                });
            }
            Map<String, Map<String, Object>> propertyMap = JsonUtil.fromJsonQuietly(
                    properties.toString(),
                    new TypeReference<Map<String, Map<String, Object>>>() {}
            ).orElseGet(LinkedHashMap::new);
            List<String> paramSummaries = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : propertyMap.entrySet()) {
                if (paramSummaries.size() >= 8) {
                    break;
                }
                String name = entry.getKey();
                Map<String, Object> node = entry.getValue() == null ? Map.of() : entry.getValue();
                String type = String.valueOf(node.getOrDefault("type", "any"));
                Object descRaw = node.get("description");
                String desc = descRaw == null ? "" : descRaw.toString().trim();
                String requiredText = required.contains(name) ? "required" : "optional";
                String text = name + " (" + type + ", " + requiredText + ")";
                if (!desc.isBlank()) {
                    text += ": " + desc;
                }
                paramSummaries.add(text);
            }
            if (paramSummaries.isEmpty()) {
                return base;
            }
            String summary = "Args: " + String.join("; ", paramSummaries);
            if (base.isBlank()) {
                return summary;
            }
            return base + " " + summary;
        } catch (Exception ignored) {
            return base;
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = value == null ? "" : value.trim();
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }
}
