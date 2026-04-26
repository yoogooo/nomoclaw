package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentToolRelationEntity;
import ai.nomoclaw.bot.store.entity.ToolDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentToolRelationRepository;
import ai.nomoclaw.bot.store.repository.ToolDefinitionRepository;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

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

    public ToolSpecificationRegistry(AgentDefinitionRepository agentDefinitionRepository,
                                     ToolDefinitionRepository toolDefinitionRepository,
                                     AgentToolRelationRepository agentToolRelationRepository) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.agentToolRelationRepository = agentToolRelationRepository;
        this.toolSpecifications = List.of(
                ToolSpecification.builder()
                        .name("command_tool")
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
                        .name("browser_tool")
                        .description("Operate a browser page to open URLs, click, type, extract text, or take screenshots. Screenshot and download output paths default to the current agent tmp directory.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Browser action arguments")
                                .addEnumProperty("action", List.of("open", "click", "type", "extract_text", "screenshot", "download"), "Browser action name")
                                .addStringProperty("url", "URL to open for action=open")
                                .addStringProperty("selector", "Target selector for click, type, extract_text, screenshot, download")
                                .addStringProperty("text", "Input text for action=type")
                                .addStringProperty("output", "Output path or directory for screenshot/download. Relative paths are resolved from the current agent workspace. Omit to save into tmp/.")
                                .required("action")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("browser_control_tool")
                        .description("Operate the browser with action-based controls. Screenshot and download output paths default to the current agent tmp directory.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Browser control arguments")
                                .addEnumProperty("action", List.of("open", "navigate", "navigate_back", "click", "type", "extract_text", "screenshot", "download", "snapshot", "wait_for", "press_key", "close"), "Browser action name")
                                .addStringProperty("url", "URL for open or navigate")
                                .addStringProperty("selector", "Target selector")
                                .addStringProperty("text", "Input text")
                                .addStringProperty("output", "Output path or directory for screenshot/download. Relative paths are resolved from the current agent workspace. Omit to save into tmp/.")
                                .addStringProperty("key", "Keyboard key")
                                .additionalProperties(true)
                                .required("action")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("file_tool")
                        .description("Read, list, or write local files. Relative paths are resolved from the current agent workspace; final deliverables should be written under report/.")
                        .parameters(JsonObjectSchema.builder()
                                .description("File tool arguments")
                                .addEnumProperty("action", List.of("read", "list", "write", "append", "edit"), "File action name")
                                .addStringProperty("path", "File or directory path")
                                .addStringProperty("content", "Content to write for action=write")
                                .required("action", "path")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("file_io_tool")
                        .description("Read, write, append, edit, or list local files. Relative paths are resolved from the current agent workspace; final deliverables should be written under report/.")
                        .parameters(JsonObjectSchema.builder()
                                .description("File I/O arguments")
                                .addEnumProperty("action", List.of("read", "list", "write", "append", "edit"), "File action name")
                                .addStringProperty("path", "File or directory path")
                                .addStringProperty("content", "Content to write or append")
                                .addIntegerProperty("startLine", "Start line for read")
                                .addIntegerProperty("endLine", "End line for read")
                                .addStringProperty("oldText", "Old text for edit")
                                .addStringProperty("newText", "New text for edit")
                                .required("action", "path")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("CronCreateTool")
                        .description("Create a persisted scheduled task using Quartz cron syntax. The expression must be a valid Quartz cron expression. Relative-time requests such as 'in 5 minutes' must be calculated strictly from the current local time and local timezone provided in the environment context. Do not execute the task immediately unless the user explicitly asks for both scheduling and immediate execution. Each run will execute the task with the current agent, write a markdown report under report/cron, and invoke the notification interface. To modify an existing scheduled task, first use CronListTool to find the old jobUid, then CronDeleteTool, then CronCreateTool with the replacement schedule.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Cron create arguments")
                                .addStringProperty("title", "Short task title for display in the management UI. Omit to derive a title from task.")
                                .addStringProperty("expression", "A valid Quartz cron expression, for example `0 0 6 ? * *`.")
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
                        .name("file_search_tool")
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
                        .name("desktop_screenshot_tool")
                        .description("Capture a desktop screenshot on the local machine. Output defaults to the current agent tmp directory.")
                        .parameters(JsonObjectSchema.builder()
                                .description("Desktop screenshot arguments")
                                .addStringProperty("path", "Output image path. Relative paths are resolved from the current agent workspace. Omit to save into tmp/.")
                                .addBooleanProperty("captureWindow", "Whether to capture a selected window")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("image_loader_tool")
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
                        .name("current_time_tool")
                        .description("Get the current UTC time.")
                        .parameters(JsonObjectSchema.builder()
                                .description("No arguments required")
                                .additionalProperties(true)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("token_usage_tool")
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
                        .name("memory_search_tool")
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
        return toolSpecifications;
    }

    public List<ToolSpecification> listForAgent(String agentName) {
        List<String> enabledToolKeys = enabledToolKeys(agentName);
        if (enabledToolKeys == null) {
            return toolSpecifications;
        }
        return enabledToolKeys.stream()
                .map(toolSpecificationsByName::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean isToolAllowed(String agentName, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        List<String> enabledToolKeys = enabledToolKeys(agentName);
        if (enabledToolKeys == null) {
            return toolSpecificationsByName.containsKey(toolName);
        }
        return enabledToolKeys.contains(toolName);
    }

    private List<String> enabledToolKeys(String agentName) {
        AgentDefinitionEntity agent = agentDefinitionRepository.findActiveByName(agentName);
        if (agent == null) {
            return null;
        }

        List<AgentToolRelationEntity> relations = agentToolRelationRepository.listActiveByAgentUid(agent.getAgentUid());
        if (relations.isEmpty()) {
            // No active relation means "no tools enabled" for this agent.
            return List.of();
        }

        Set<String> builtinKeys = toolSpecificationsByName.keySet();
        Set<String> activeDefinitionKeys = toolDefinitionRepository.listActiveByKeys(
                        relations.stream().map(AgentToolRelationEntity::getToolKey).toList())
                .stream()
                .map(ToolDefinitionEntity::getToolKey)
                .collect(Collectors.toSet());

        return relations.stream()
                .map(AgentToolRelationEntity::getToolKey)
                .filter(activeDefinitionKeys::contains)
                .filter(builtinKeys::contains)
                .distinct()
                .toList();
    }
}
