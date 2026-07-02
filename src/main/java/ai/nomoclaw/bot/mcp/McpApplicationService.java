package ai.nomoclaw.bot.mcp;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.orchestrator.SystemErrorLogService;
import ai.nomoclaw.bot.store.entity.AgentMcpToolRelationEntity;
import ai.nomoclaw.bot.store.entity.McpServerDefinitionEntity;
import ai.nomoclaw.bot.store.entity.McpToolSnapshotEntity;
import ai.nomoclaw.bot.store.repository.AgentMcpToolRelationRepository;
import ai.nomoclaw.bot.store.repository.McpServerDefinitionRepository;
import ai.nomoclaw.bot.store.repository.McpToolSnapshotRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpClientMethod;
import dev.langchain4j.mcp.protocol.McpImplementation;
import dev.langchain4j.mcp.protocol.McpInitializeParams;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class McpApplicationService {
    private static final String CODEX_SERVER_NAME = "codex";
    private static final String CODEX_COMMAND = "codex";
    private static final int CODEX_TIMEOUT_SECONDS = 3600;
    private static final List<String> CODEX_MCP_SERVER_ARGS = List.of("mcp-server");

    private final McpServerDefinitionRepository serverRepository;
    private final McpToolSnapshotRepository toolSnapshotRepository;
    private final AgentMcpToolRelationRepository agentMcpToolRelationRepository;
    private final McpClientFactory clientFactory;
    private final McpToolKeyGenerator toolKeyGenerator;
    private final SystemErrorLogService systemErrorLogService;
    private final CodexAppServerBridge codexAppServerBridge;

    public McpApplicationService(McpServerDefinitionRepository serverRepository,
                                 McpToolSnapshotRepository toolSnapshotRepository,
                                 AgentMcpToolRelationRepository agentMcpToolRelationRepository,
                                 McpClientFactory clientFactory,
                                 McpToolKeyGenerator toolKeyGenerator,
                                 SystemErrorLogService systemErrorLogService,
                                 CodexAppServerBridge codexAppServerBridge) {
        this.serverRepository = serverRepository;
        this.toolSnapshotRepository = toolSnapshotRepository;
        this.agentMcpToolRelationRepository = agentMcpToolRelationRepository;
        this.clientFactory = clientFactory;
        this.toolKeyGenerator = toolKeyGenerator;
        this.systemErrorLogService = systemErrorLogService;
        this.codexAppServerBridge = codexAppServerBridge;
    }

    public List<McpServerDto> listServers() {
        Map<String, Long> toolCounts = toolSnapshotRepository.listAll().stream()
                .collect(Collectors.groupingBy(McpToolSnapshotEntity::getServerUid, Collectors.counting()));
        return serverRepository.listAll().stream()
                .map(server -> toDto(server, toolCounts.getOrDefault(server.getServerUid(), 0L).intValue()))
                .toList();
    }

    public List<McpToolDto> listTools(String serverUid) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        return toolSnapshotRepository.listByServerUid(serverUid).stream()
                .map(snapshot -> toToolDto(snapshot, server))
                .toList();
    }

    @Transactional
    public McpServerDto createServer(SaveMcpServerParam command) {
        LocalDateTime now = LocalDateTime.now();
        McpServerDefinitionEntity server = new McpServerDefinitionEntity();
        server.setServerUid(UuidUtil.newUuid());
        applyParam(server, command, now, true);
        ensureServerNameAvailable(server.getServerName(), null);
        server.setCreatedTime(now);
        serverRepository.save(server);
        return toDto(server, 0);
    }

    @Transactional
    public McpServerDto createCodexServer(CreateCodexMcpServerParam command) {
        SaveMcpServerParam saveCommand = new SaveMcpServerParam(
                defaultIfBlank(command == null ? null : command.serverName(), CODEX_SERVER_NAME),
                "STDIO",
                command == null || command.timeoutSeconds() == null ? CODEX_TIMEOUT_SECONDS : command.timeoutSeconds(),
                command == null ? Boolean.TRUE : command.autoStart(),
                "",
                Map.of(),
                defaultIfBlank(command == null ? null : command.command(), CODEX_COMMAND),
                CODEX_MCP_SERVER_ARGS,
                command == null ? Map.of() : command.env(),
                command == null ? "" : command.cwd()
        );
        return createServer(saveCommand);
    }

    @Transactional
    public McpServerDto updateServer(String serverUid, SaveMcpServerParam command) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        applyParam(server, command, LocalDateTime.now(), false);
        ensureServerNameAvailable(server.getServerName(), server.getId());
        serverRepository.updateById(server);
        return toDto(server, toolSnapshotRepository.listByServerUid(serverUid).size());
    }

    @Transactional
    public McpServerDto updateServerStatus(String serverUid, boolean enabled) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        String nextStatus = enabled ? "ACTIVE" : "DISABLED";
        LocalDateTime now = LocalDateTime.now();
        server.setStatus(nextStatus);
        server.setUpdatedTime(now);
        serverRepository.updateById(server);
        for (McpToolSnapshotEntity snapshot : toolSnapshotRepository.listByServerUid(serverUid)) {
            snapshot.setStatus(nextStatus);
            snapshot.setUpdatedTime(now);
            toolSnapshotRepository.updateById(snapshot);
        }
        return toDto(server, toolSnapshotRepository.listByServerUid(serverUid).size());
    }

    @Transactional
    public void deleteServer(String serverUid) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        for (McpToolSnapshotEntity snapshot : toolSnapshotRepository.listByServerUid(serverUid)) {
            agentMcpToolRelationRepository.deleteByToolKey(snapshot.getToolKey());
            toolSnapshotRepository.removeById(snapshot.getId());
        }
        serverRepository.removeById(server.getId());
    }

    public McpServerDto testServer(String serverUid) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        McpServerConfig serverConfig = readConfig(server);
        try (McpClient client = clientFactory.create(server, serverConfig)) {
            client.checkHealth();
            markConnected(server, "");
        } catch (Exception ex) {
            markError(server, ex);
            recordMcpError("MCP_TEST_FAILED", "MCP Server 测试失败", server, ex);
            throw new IllegalStateException("MCP server test failed: " + ex.getMessage(), ex);
        }
        return toDto(serverRepository.findByUid(serverUid), toolSnapshotRepository.listByServerUid(serverUid).size());
    }

    @Transactional
    public List<McpToolDto> refreshTools(String serverUid) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        try (McpClient client = clientFactory.create(server, readConfig(server))) {
            List<ToolSpecification> tools = client.listTools();
            if (shouldUseRawToolListRefresh(tools)) {
                log.warn("[MCP] detected empty/unsupported tool schemas from SDK listTools serverUid={} serverName={}, fallback to raw tools/list",
                        server.getServerUid(), server.getServerName());
                return refreshToolsWithRawProtocol(server);
            }
            logRefreshedTools(server, tools);
            LocalDateTime now = LocalDateTime.now();
            for (ToolSpecification tool : tools) {
                upsertToolSnapshot(server, tool, now);
            }
            markConnected(server, "");
            return listTools(serverUid);
        } catch (Exception ex) {
            if (isUnsupportedToolSchemaCast(ex)) {
                log.warn("[MCP] refresh tools fallback serverUid={} serverName={} reason={} -> keep existing snapshots",
                        server.getServerUid(), server.getServerName(), ex.getMessage());
                return refreshToolsWithRawProtocol(server);
            }
            markError(server, ex);
            recordMcpError("MCP_REFRESH_TOOLS_FAILED", "MCP 工具刷新失败", server, ex);
            throw new IllegalStateException("MCP tools refresh failed: " + ex.getMessage(), ex);
        }
    }

    private boolean shouldUseRawToolListRefresh(List<ToolSpecification> tools) {
        if (tools == null || tools.isEmpty()) {
            return false;
        }
        int nonEmptySchemas = 0;
        for (ToolSpecification tool : tools) {
            String schemaJson = serializeToolParameters(tool);
            if (schemaJson == null) {
                continue;
            }
            String normalized = schemaJson.trim();
            if (!normalized.isBlank() && !"{}".equals(normalized)) {
                nonEmptySchemas++;
            }
        }
        return nonEmptySchemas == 0;
    }

    private List<McpToolDto> refreshToolsWithRawProtocol(McpServerDefinitionEntity server) {
        try (McpTransport transport = clientFactory.createTransport(server, readConfig(server))) {
            Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
            McpOperationHandler handler = new McpOperationHandler(
                    pending,
                    Collections::emptyList,
                    transport,
                    ignored -> {
                    },
                    () -> {
                    },
                    () -> {
                    },
                    () -> {
                    },
                    ignored -> {
                    },
                    ignored -> {
                    },
                    () -> {
                    },
                    () -> {
                    },
                    (ignoredOperationId, ignoredReason) -> {
                    }
            );
            transport.start(handler);

            long id = 1L;
            McpInitializeRequest initializeRequest = new McpInitializeRequest(id++);
            initializeRequest.setParams(buildInitializeParams());
            transport.initialize(initializeRequest).get(30, TimeUnit.SECONDS);
            transport.executeOperationWithoutResponse(new McpClientMessage(null, McpClientMethod.NOTIFICATION_INITIALIZED));

            List<RawMcpTool> tools = new ArrayList<>();
            String cursor = null;
            do {
                McpListToolsRequest request = new McpListToolsRequest(id++, cursor);
                JsonNode response = transport.executeOperationWithResponse(request).get(30, TimeUnit.SECONDS);
                JsonNode result = response == null ? null : response.path("result");
                JsonNode toolArray = result == null ? null : result.path("tools");
                if (toolArray != null && toolArray.isArray()) {
                    for (JsonNode item : toolArray) {
                        String name = item.path("name").asText("");
                        if (name.isBlank()) {
                            continue;
                        }
                        String description = item.path("description").asText("");
                        JsonNode inputSchema = item.path("inputSchema");
                        if (inputSchema == null || inputSchema.isMissingNode() || inputSchema.isNull()) {
                            inputSchema = item.path("input_schema");
                        }
                        String inputSchemaJson = (inputSchema == null || inputSchema.isMissingNode() || inputSchema.isNull())
                                ? "{}"
                                : inputSchema.toString();
                        tools.add(new RawMcpTool(name, description, inputSchemaJson));
                    }
                }
                cursor = result == null ? "" : result.path("nextCursor").asText("");
            } while (cursor != null && !cursor.isBlank());

            logRefreshedRawTools(server, tools);
            LocalDateTime now = LocalDateTime.now();
            for (RawMcpTool tool : tools) {
                upsertToolSnapshot(server, tool, now);
            }
            markConnected(server, "");
            return listTools(server.getServerUid());
        } catch (Exception rawEx) {
            markError(server, rawEx);
            recordMcpError("MCP_REFRESH_TOOLS_FAILED", "MCP 工具刷新失败", server, rawEx);
            throw new IllegalStateException("MCP tools refresh failed: " + rawEx.getMessage(), rawEx);
        }
    }

    private McpInitializeParams buildInitializeParams() {
        McpInitializeParams params = new McpInitializeParams();
        params.setProtocolVersion("2024-11-05");
        McpImplementation clientInfo = new McpImplementation();
        clientInfo.setName("nomoclaw");
        clientInfo.setVersion("1.0");
        params.setClientInfo(clientInfo);
        McpInitializeParams.Capabilities capabilities = new McpInitializeParams.Capabilities();
        McpInitializeParams.Capabilities.Roots roots = new McpInitializeParams.Capabilities.Roots();
        roots.setListChanged(true);
        capabilities.setRoots(roots);
        params.setCapabilities(capabilities);
        return params;
    }

    private boolean isUnsupportedToolSchemaCast(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = nullToEmpty(current.getMessage());
            if (message.contains("JsonAnyOfSchema")
                    && message.contains("JsonObjectSchema")
                    && message.contains("cannot be cast")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public boolean isMcpTool(String toolKey) {
        return toolSnapshotRepository.findActiveByToolKey(toolKey) != null;
    }

    public List<AgentMcpToolDto> listAgentTools(String agentUid) {
        Map<String, AgentMcpToolRelationEntity> relationsByToolKey = agentMcpToolRelationRepository.listByAgentUid(agentUid)
                .stream()
                .collect(Collectors.toMap(
                        AgentMcpToolRelationEntity::getToolKey,
                        relation -> relation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, McpServerDefinitionEntity> serversByUid = serverRepository.listAll().stream()
                .collect(Collectors.toMap(
                        McpServerDefinitionEntity::getServerUid,
                        server -> server,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return toolSnapshotRepository.listActive().stream()
                .map(snapshot -> toAgentToolDto(snapshot, serversByUid.get(snapshot.getServerUid()), relationsByToolKey.get(snapshot.getToolKey())))
                .toList();
    }

    @Transactional
    public AgentMcpToolDto updateAgentToolStatus(String agentUid, String toolKey, boolean enabled) {
        String normalizedAgentUid = nullToEmpty(agentUid).trim();
        String normalizedToolKey = nullToEmpty(toolKey).trim();
        if (normalizedAgentUid.isBlank()) {
            throw new IllegalArgumentException("agentUid must not be blank");
        }
        if (normalizedToolKey.isBlank()) {
            throw new IllegalArgumentException("toolKey must not be blank");
        }
        McpToolSnapshotEntity snapshot = toolSnapshotRepository.findActiveByToolKey(normalizedToolKey);
        if (snapshot == null) {
            throw new IllegalArgumentException("MCP tool not found: " + normalizedToolKey);
        }

        AgentMcpToolRelationEntity relation = agentMcpToolRelationRepository.findByAgentUidAndToolKey(normalizedAgentUid, normalizedToolKey);
        LocalDateTime now = LocalDateTime.now();
        String nextStatus = enabled ? "ACTIVE" : "DISABLED";
        if (relation == null) {
            relation = new AgentMcpToolRelationEntity();
            relation.setRelationUid(UuidUtil.newUuid());
            relation.setAgentUid(normalizedAgentUid);
            relation.setToolKey(normalizedToolKey);
            relation.setSortIndex(500);
            relation.setConfigJson("{}");
            relation.setCreatedTime(now);
        }
        relation.setStatus(nextStatus);
        relation.setUpdatedTime(now);
        if (relation.getId() == null) {
            agentMcpToolRelationRepository.save(relation);
        } else {
            agentMcpToolRelationRepository.updateById(relation);
        }
        return toAgentToolDto(snapshot, serverRepository.findByUid(snapshot.getServerUid()), relation);
    }

    public void initializeAgentToolRelations(String agentUid, LocalDateTime now) {
        String normalizedAgentUid = nullToEmpty(agentUid).trim();
        if (normalizedAgentUid.isBlank()) {
            return;
        }
        for (McpToolSnapshotEntity snapshot : toolSnapshotRepository.listActive()) {
            AgentMcpToolRelationEntity relation = new AgentMcpToolRelationEntity();
            relation.setRelationUid(UuidUtil.newUuid());
            relation.setAgentUid(normalizedAgentUid);
            relation.setToolKey(snapshot.getToolKey());
            relation.setStatus("ACTIVE");
            relation.setSortIndex(500);
            relation.setConfigJson("{}");
            relation.setCreatedTime(now);
            relation.setUpdatedTime(now);
            agentMcpToolRelationRepository.save(relation);
        }
    }

    public ToolResult execute(String toolKey, ToolRequest request) {
        McpToolSnapshotEntity snapshot = toolSnapshotRepository.findActiveByToolKey(toolKey);
        if (snapshot == null) {
            return ToolResult.failure("MCP_TOOL_NOT_FOUND", "MCP tool not found: " + toolKey, JsonNodeFactory.instance.objectNode());
        }
        McpServerDefinitionEntity server = requireServer(snapshot.getServerUid());
        if (!"ACTIVE".equalsIgnoreCase(server.getStatus())) {
            return ToolResult.failure("MCP_SERVER_DISABLED", "MCP server is disabled: " + server.getServerName(), JsonNodeFactory.instance.objectNode());
        }
        String requestArgsJson = request.args() == null ? "{}" : request.args().toString();
        log.info("[MCP] execute toolKey={} originalToolName={} serverUid={} args={}",
                toolKey, snapshot.getOriginalToolName(), server.getServerUid(), truncateForLog(requestArgsJson, 1600));
        McpServerConfig serverConfig = readConfig(server);
        if (codexAppServerBridge.supports(snapshot, server, serverConfig)) {
            return codexAppServerBridge.execute(snapshot, server, serverConfig, request);
        }
        try (McpClient client = clientFactory.create(server, readConfig(server))) {
            ToolExecutionRequest executionRequest = ToolExecutionRequest.builder()
                    .name(snapshot.getOriginalToolName())
                    .arguments(requestArgsJson)
                    .build();
            ToolExecutionResult executionResult = client.executeTool(executionRequest);
            if (executionResult != null && executionResult.isError()) {
                String resultText = nullToEmpty(executionResult.resultText());
                log.warn("[MCP] execute failed toolKey={} originalToolName={} serverUid={} result={}",
                        toolKey, snapshot.getOriginalToolName(), server.getServerUid(), truncateForLog(resultText, 1600));
                return ToolResult.failure("MCP_TOOL_EXECUTION_FAILED", executionResult.resultText(), JsonNodeFactory.instance.objectNode());
            }
            String output = executionResult == null ? "" : executionResult.resultText();
            log.info("[MCP] execute success toolKey={} originalToolName={} serverUid={} result={}",
                    toolKey, snapshot.getOriginalToolName(), server.getServerUid(), truncateForLog(output == null ? "" : output, 1600));
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("serverUid", server.getServerUid());
            artifacts.put("serverName", server.getServerName());
            artifacts.put("originalToolName", snapshot.getOriginalToolName());
            return ToolResult.success(output == null ? "" : output, artifacts, JsonNodeFactory.instance.objectNode());
        } catch (Exception ex) {
            markError(server, ex);
            recordMcpError("MCP_TOOL_EXECUTION_FAILED", "MCP 工具执行失败", server, ex);
            ObjectNode metrics = JsonNodeFactory.instance.objectNode();
            metrics.put("serverUid", server.getServerUid());
            metrics.put("toolKey", toolKey);
            return ToolResult.failure("MCP_TOOL_EXECUTION_FAILED", ex.getMessage(), metrics);
        }
    }

    public List<McpToolSnapshotEntity> listActiveToolSnapshotsByKeys(List<String> toolKeys) {
        return toolSnapshotRepository.listActiveByToolKeys(toolKeys);
    }

    public List<McpToolSnapshotEntity> listActiveToolSnapshots() {
        return toolSnapshotRepository.listActive();
    }

    public List<McpToolSnapshotEntity> listActiveToolSnapshotsForAgent(String agentUid) {
        List<String> toolKeys = agentMcpToolRelationRepository.listActiveByAgentUid(agentUid).stream()
                .map(AgentMcpToolRelationEntity::getToolKey)
                .distinct()
                .toList();
        return toolSnapshotRepository.listActiveByToolKeys(toolKeys);
    }

    private void logRefreshedTools(McpServerDefinitionEntity server, List<ToolSpecification> tools) {
        String toolList = tools.stream()
                .map(tool -> "%s(description=%s)".formatted(
                        nullToEmpty(tool.name()),
                        truncateForLog(nullToEmpty(tool.description()), 240)
                ))
                .collect(Collectors.joining(", "));
        log.info("[MCP] refreshed tools serverUid={} serverName={} count={} tools=[{}]",
                server.getServerUid(), server.getServerName(), tools.size(), toolList);
    }

    private void logRefreshedRawTools(McpServerDefinitionEntity server, List<RawMcpTool> tools) {
        String toolList = tools.stream()
                .map(tool -> "%s(description=%s)".formatted(
                        nullToEmpty(tool.originalToolName()),
                        truncateForLog(nullToEmpty(tool.description()), 240)
                ))
                .collect(Collectors.joining(", "));
        log.info("[MCP] refreshed tools(raw) serverUid={} serverName={} count={} tools=[{}]",
                server.getServerUid(), server.getServerName(), tools.size(), toolList);
    }

    private String truncateForLog(String value, int maxLength) {
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void upsertToolSnapshot(McpServerDefinitionEntity server, ToolSpecification tool, LocalDateTime now) {
        String originalName = tool.name();
        McpToolSnapshotEntity snapshot = toolSnapshotRepository.findByServerUidAndOriginalToolName(server.getServerUid(), originalName);
        String toolKey = toolKeyGenerator.generate(server.getServerName(), originalName);
        if (snapshot == null) {
            snapshot = new McpToolSnapshotEntity();
            snapshot.setSnapshotUid(UuidUtil.newUuid());
            snapshot.setServerUid(server.getServerUid());
            snapshot.setOriginalToolName(originalName);
            snapshot.setCreatedTime(now);
        } else {
            agentMcpToolRelationRepository.updateToolKey(snapshot.getToolKey(), toolKey);
        }
        snapshot.setToolKey(toolKey);
        snapshot.setDescription(nullToEmpty(tool.description()));
        snapshot.setInputSchemaJson(serializeToolParameters(tool));
        snapshot.setStatus(server.getStatus());
        snapshot.setLastSyncedTime(now);
        snapshot.setUpdatedTime(now);
        if (snapshot.getId() == null) {
            toolSnapshotRepository.save(snapshot);
        } else {
            toolSnapshotRepository.updateById(snapshot);
        }
    }

    private void upsertToolSnapshot(McpServerDefinitionEntity server, RawMcpTool tool, LocalDateTime now) {
        String originalName = tool.originalToolName();
        McpToolSnapshotEntity snapshot = toolSnapshotRepository.findByServerUidAndOriginalToolName(server.getServerUid(), originalName);
        String toolKey = toolKeyGenerator.generate(server.getServerName(), originalName);
        if (snapshot == null) {
            snapshot = new McpToolSnapshotEntity();
            snapshot.setSnapshotUid(UuidUtil.newUuid());
            snapshot.setServerUid(server.getServerUid());
            snapshot.setOriginalToolName(originalName);
            snapshot.setCreatedTime(now);
        } else {
            agentMcpToolRelationRepository.updateToolKey(snapshot.getToolKey(), toolKey);
        }
        snapshot.setToolKey(toolKey);
        snapshot.setDescription(nullToEmpty(tool.description()));
        snapshot.setInputSchemaJson(tool.inputSchemaJson());
        snapshot.setStatus(server.getStatus());
        snapshot.setLastSyncedTime(now);
        snapshot.setUpdatedTime(now);
        if (snapshot.getId() == null) {
            toolSnapshotRepository.save(snapshot);
        } else {
            toolSnapshotRepository.updateById(snapshot);
        }
    }

    private String resolveToolDisplayName(McpServerDefinitionEntity server, String originalToolName) {
        String name = nullToEmpty(originalToolName);
        String serverName = server == null ? "" : server.getServerName();
        return "MCP · " + serverName + " · " + name;
    }

    private String serializeToolParameters(ToolSpecification tool) {
        try {
            return JsonUtil.toJson(tool.parameters());
        } catch (Exception ex) {
            log.debug("Failed to serialize MCP tool parameters for {}", tool.name(), ex);
            return "{}";
        }
    }

    private void applyParam(McpServerDefinitionEntity server,
                              SaveMcpServerParam command,
                              LocalDateTime now,
                              boolean creating) {
        String serverName = normalizeServerName(command.serverName());
        String transport = normalizeTransport(command.transport());
        server.setServerName(serverName);
        server.setTransport(transport);
        if (creating) {
            server.setStatus("ACTIVE");
        }
        server.setTimeoutSeconds(command.timeoutSeconds() == null || command.timeoutSeconds() <= 0 ? 30 : command.timeoutSeconds());
        server.setAutoStart(command.autoStart() == null || command.autoStart());
        server.setConfigJson(JsonUtil.toJson(new McpServerConfig(
                nullToEmpty(command.endpoint()).trim(),
                sanitizeMap(command.headers()),
                nullToEmpty(command.command()).trim(),
                command.args() == null ? List.of() : command.args().stream().filter(value -> value != null && !value.isBlank()).toList(),
                sanitizeMap(command.env()),
                nullToEmpty(command.cwd()).trim()
        )));
        server.setUpdatedTime(now);
        if (creating) {
            server.setLastError("");
        }
    }

    private String normalizeServerName(String value) {
        String normalized = nullToEmpty(value).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("serverName is required");
        }
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = nullToEmpty(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private void ensureServerNameAvailable(String serverName, Long currentId) {
        McpServerDefinitionEntity existing = serverRepository.findByServerName(serverName);
        if (existing != null && (currentId == null || !currentId.equals(existing.getId()))) {
            throw new IllegalArgumentException("MCP server name already exists: " + serverName);
        }
    }

    private String normalizeTransport(String value) {
        String normalized = nullToEmpty(value).trim().toUpperCase(Locale.ROOT);
        if ("STDIO".equals(normalized)) {
            return "STDIO";
        }
        if ("HTTP".equals(normalized) || "STREAMABLE_HTTP".equals(normalized)) {
            return "HTTP";
        }
        throw new IllegalArgumentException("transport must be HTTP or STDIO");
    }

    private Map<String, String> sanitizeMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                result.put(key.trim(), value == null ? "" : value.trim());
            }
        });
        return result;
    }

    private McpServerDefinitionEntity requireServer(String serverUid) {
        McpServerDefinitionEntity server = serverRepository.findByUid(serverUid);
        if (server == null) {
            throw new IllegalArgumentException("MCP server not found: " + serverUid);
        }
        return server;
    }

    private McpServerDefinitionEntity requireActiveServer(String serverUid) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        if (!"ACTIVE".equalsIgnoreCase(server.getStatus())) {
            throw new IllegalArgumentException("MCP server is disabled: " + serverUid);
        }
        return server;
    }

    private McpServerConfig readConfig(McpServerDefinitionEntity server) {
        String configJson = server.getConfigJson();
        if (configJson == null || configJson.isBlank()) {
            return McpServerConfig.empty();
        }
        return JsonUtil.fromJsonQuietly(configJson, McpServerConfig.class).orElseGet(() -> {
            Map<String, Object> raw = JsonUtil.fromJsonQuietly(configJson, new TypeReference<Map<String, Object>>() {})
                    .orElse(Map.of());
            return McpServerConfig.empty();
        });
    }

    private McpServerDto toDto(McpServerDefinitionEntity server, int toolCount) {
        McpServerConfig config = readConfig(server);
        return new McpServerDto(
                server.getServerUid(),
                server.getServerName(),
                server.getTransport(),
                server.getStatus(),
                server.getTimeoutSeconds(),
                server.getAutoStart(),
                config.endpoint(),
                config.headers(),
                config.command(),
                config.args(),
                config.env(),
                config.cwd(),
                server.getLastConnectedTime(),
                server.getLastError(),
                toolCount,
                server.getCreatedTime(),
                server.getUpdatedTime()
        );
    }

    private McpToolDto toToolDto(McpToolSnapshotEntity snapshot, McpServerDefinitionEntity server) {
        return new McpToolDto(
                snapshot.getToolKey(),
                snapshot.getServerUid(),
                snapshot.getOriginalToolName(),
                resolveToolDisplayName(server, snapshot.getOriginalToolName()),
                snapshot.getDescription(),
                snapshot.getInputSchemaJson(),
                snapshot.getStatus(),
                snapshot.getLastSyncedTime()
        );
    }

    private AgentMcpToolDto toAgentToolDto(McpToolSnapshotEntity snapshot,
                                           McpServerDefinitionEntity server,
                                           AgentMcpToolRelationEntity relation) {
        boolean enabled = relation != null && "ACTIVE".equalsIgnoreCase(relation.getStatus());
        LocalDateTime updatedTime = relation == null ? snapshot.getUpdatedTime() : relation.getUpdatedTime();
        String serverName = server == null ? "" : server.getServerName();
        String serverDisplayName = serverName;
        return new AgentMcpToolDto(
                snapshot.getToolKey(),
                snapshot.getServerUid(),
                serverName,
                serverDisplayName,
                snapshot.getOriginalToolName(),
                resolveToolDisplayName(server, snapshot.getOriginalToolName()),
                snapshot.getDescription(),
                enabled,
                updatedTime
        );
    }

    private void markConnected(McpServerDefinitionEntity server, String error) {
        server.setLastConnectedTime(LocalDateTime.now());
        server.setLastError(error == null ? "" : error);
        server.setUpdatedTime(LocalDateTime.now());
        serverRepository.updateById(server);
    }

    private void markError(McpServerDefinitionEntity server, Exception ex) {
        server.setLastError(ex == null ? "unknown error" : nullToEmpty(ex.getMessage()));
        server.setUpdatedTime(LocalDateTime.now());
        serverRepository.updateById(server);
    }

    private void recordMcpError(String code, String title, McpServerDefinitionEntity server, Exception ex) {
        String serverName = server == null ? "" : server.getServerName();
        systemErrorLogService.recordException(
                "ERROR",
                "MCP",
                code,
                title,
                "MCP server failed: " + serverName + " - " + (ex == null ? "unknown error" : nullToEmpty(ex.getMessage())),
                ex
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record RawMcpTool(String originalToolName, String description, String inputSchemaJson) {
    }
}
