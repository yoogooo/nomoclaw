package ai.nomoclaw.bot.mcp;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.store.entity.AgentMcpToolRelationEntity;
import ai.nomoclaw.bot.store.entity.McpServerDefinitionEntity;
import ai.nomoclaw.bot.store.entity.McpToolSnapshotEntity;
import ai.nomoclaw.bot.store.repository.AgentMcpToolRelationRepository;
import ai.nomoclaw.bot.store.repository.McpServerDefinitionRepository;
import ai.nomoclaw.bot.store.repository.McpToolSnapshotRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class McpApplicationService {

    private final McpServerDefinitionRepository serverRepository;
    private final McpToolSnapshotRepository toolSnapshotRepository;
    private final AgentMcpToolRelationRepository agentMcpToolRelationRepository;
    private final McpClientFactory clientFactory;
    private final McpToolKeyGenerator toolKeyGenerator;

    public McpApplicationService(McpServerDefinitionRepository serverRepository,
                                 McpToolSnapshotRepository toolSnapshotRepository,
                                 AgentMcpToolRelationRepository agentMcpToolRelationRepository,
                                 McpClientFactory clientFactory,
                                 McpToolKeyGenerator toolKeyGenerator) {
        this.serverRepository = serverRepository;
        this.toolSnapshotRepository = toolSnapshotRepository;
        this.agentMcpToolRelationRepository = agentMcpToolRelationRepository;
        this.clientFactory = clientFactory;
        this.toolKeyGenerator = toolKeyGenerator;
    }

    public List<McpServerDto> listServers() {
        Map<String, Long> toolCounts = toolSnapshotRepository.listAll().stream()
                .collect(Collectors.groupingBy(McpToolSnapshotEntity::getServerUid, Collectors.counting()));
        return serverRepository.listAll().stream()
                .map(server -> toDto(server, toolCounts.getOrDefault(server.getServerUid(), 0L).intValue()))
                .toList();
    }

    public List<McpToolDto> listTools(String serverUid) {
        requireServer(serverUid);
        return toolSnapshotRepository.listByServerUid(serverUid).stream()
                .map(this::toToolDto)
                .toList();
    }

    @Transactional
    public McpServerDto createServer(SaveMcpServerCommand command) {
        LocalDateTime now = LocalDateTime.now();
        McpServerDefinitionEntity server = new McpServerDefinitionEntity();
        server.setServerUid(UUID.randomUUID().toString());
        applyCommand(server, command, now, true);
        server.setCreatedTime(now);
        serverRepository.save(server);
        return toDto(server, 0);
    }

    @Transactional
    public McpServerDto updateServer(String serverUid, SaveMcpServerCommand command) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        applyCommand(server, command, LocalDateTime.now(), false);
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
        try (McpClient client = clientFactory.create(server, readConfig(server))) {
            client.checkHealth();
            markConnected(server, "");
        } catch (Exception ex) {
            markError(server, ex);
            throw new IllegalStateException("MCP server test failed: " + ex.getMessage(), ex);
        }
        return toDto(serverRepository.findByUid(serverUid), toolSnapshotRepository.listByServerUid(serverUid).size());
    }

    @Transactional
    public List<McpToolDto> refreshTools(String serverUid) {
        McpServerDefinitionEntity server = requireServer(serverUid);
        try (McpClient client = clientFactory.create(server, readConfig(server))) {
            List<ToolSpecification> tools = client.listTools();
            logRefreshedTools(server, tools);
            LocalDateTime now = LocalDateTime.now();
            for (ToolSpecification tool : tools) {
                upsertToolSnapshot(server, tool, now);
            }
            markConnected(server, "");
            return listTools(serverUid);
        } catch (Exception ex) {
            markError(server, ex);
            throw new IllegalStateException("MCP tools refresh failed: " + ex.getMessage(), ex);
        }
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
            relation.setRelationUid(UUID.randomUUID().toString());
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

    public ToolResult execute(String toolKey, ToolRequest request) {
        McpToolSnapshotEntity snapshot = toolSnapshotRepository.findActiveByToolKey(toolKey);
        if (snapshot == null) {
            return ToolResult.failure("MCP_TOOL_NOT_FOUND", "MCP tool not found: " + toolKey, JsonNodeFactory.instance.objectNode());
        }
        McpServerDefinitionEntity server = requireServer(snapshot.getServerUid());
        if (!"ACTIVE".equalsIgnoreCase(server.getStatus())) {
            return ToolResult.failure("MCP_SERVER_DISABLED", "MCP server is disabled: " + server.getServerName(), JsonNodeFactory.instance.objectNode());
        }
        try (McpClient client = clientFactory.create(server, readConfig(server))) {
            ToolExecutionRequest executionRequest = ToolExecutionRequest.builder()
                    .name(snapshot.getOriginalToolName())
                    .arguments(request.args() == null ? "{}" : request.args().toString())
                    .build();
            ToolExecutionResult executionResult = client.executeTool(executionRequest);
            if (executionResult != null && executionResult.isError()) {
                return ToolResult.failure("MCP_TOOL_EXECUTION_FAILED", executionResult.resultText(), JsonNodeFactory.instance.objectNode());
            }
            String output = executionResult == null ? "" : executionResult.resultText();
            ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
            artifacts.put("serverUid", server.getServerUid());
            artifacts.put("serverName", server.getServerName());
            artifacts.put("originalToolName", snapshot.getOriginalToolName());
            return ToolResult.success(output == null ? "" : output, artifacts, JsonNodeFactory.instance.objectNode());
        } catch (Exception ex) {
            markError(server, ex);
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
        String toolKey = snapshot == null
                ? toolKeyGenerator.generate(server.getServerName(), originalName)
                : snapshot.getToolKey();
        if (snapshot == null) {
            snapshot = new McpToolSnapshotEntity();
            snapshot.setSnapshotUid(UUID.randomUUID().toString());
            snapshot.setServerUid(server.getServerUid());
            snapshot.setToolKey(toolKey);
            snapshot.setOriginalToolName(originalName);
            snapshot.setCreatedTime(now);
        }
        snapshot.setDisplayName(resolveToolDisplayName(server, tool));
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

    private String resolveToolDisplayName(McpServerDefinitionEntity server, ToolSpecification tool) {
        String name = nullToEmpty(tool.name());
        String serverDisplay = nullToEmpty(server.getDisplayName()).isBlank() ? server.getServerName() : server.getDisplayName();
        return "MCP · " + serverDisplay + " · " + name;
    }

    private String serializeToolParameters(ToolSpecification tool) {
        try {
            return JsonUtil.toJson(tool.parameters());
        } catch (Exception ex) {
            log.debug("Failed to serialize MCP tool parameters for {}", tool.name(), ex);
            return "{}";
        }
    }

    private void applyCommand(McpServerDefinitionEntity server,
                              SaveMcpServerCommand command,
                              LocalDateTime now,
                              boolean creating) {
        String serverName = normalizeServerName(command.serverName());
        String transport = normalizeTransport(command.transport());
        server.setServerName(serverName);
        server.setDisplayName(nullToEmpty(command.displayName()).isBlank() ? serverName : command.displayName().trim());
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
        String normalized = nullToEmpty(value).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("serverName is required");
        }
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
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
                server.getDisplayName(),
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

    private McpToolDto toToolDto(McpToolSnapshotEntity snapshot) {
        return new McpToolDto(
                snapshot.getToolKey(),
                snapshot.getServerUid(),
                snapshot.getOriginalToolName(),
                snapshot.getDisplayName(),
                snapshot.getDescription(),
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
        String serverDisplayName = server == null ? "" : server.getDisplayName();
        return new AgentMcpToolDto(
                snapshot.getToolKey(),
                snapshot.getServerUid(),
                serverName,
                serverDisplayName,
                snapshot.getOriginalToolName(),
                snapshot.getDisplayName(),
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
