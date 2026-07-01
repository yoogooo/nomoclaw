package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.mcp.request.CreateCodexMcpServerRequest;
import ai.nomoclaw.bot.api.dto.mcp.request.SaveMcpServerRequest;
import ai.nomoclaw.bot.mcp.CreateCodexMcpServerParam;
import ai.nomoclaw.bot.api.dto.mcp.response.McpServerResponse;
import ai.nomoclaw.bot.api.dto.mcp.response.McpToolResponse;
import ai.nomoclaw.bot.mcp.McpServerDto;
import ai.nomoclaw.bot.mcp.McpToolDto;
import ai.nomoclaw.bot.mcp.SaveMcpServerParam;

import java.util.List;

/**
 * Maps MCP request/response payloads.
 */
public final class McpApiMapper {

    private McpApiMapper() {
    }

    public static SaveMcpServerParam toParam(SaveMcpServerRequest request) {
        return new SaveMcpServerParam(
                request.serverName(),
                request.transport(),
                request.timeoutSeconds(),
                request.autoStart(),
                request.endpoint(),
                request.headers(),
                request.command(),
                request.args(),
                request.env(),
                request.cwd()
        );
    }

    public static CreateCodexMcpServerParam toParam(CreateCodexMcpServerRequest request) {
        return new CreateCodexMcpServerParam(
                request == null ? null : request.serverName(),
                request == null ? null : request.timeoutSeconds(),
                request == null ? null : request.autoStart(),
                request == null ? null : request.command(),
                request == null ? null : request.cwd(),
                request == null ? null : request.env()
        );
    }

    public static List<McpServerResponse> toMcpServers(List<McpServerDto> dtos) {
        return dtos.stream().map(McpApiMapper::toMcpServer).toList();
    }

    public static McpServerResponse toMcpServer(McpServerDto dto) {
        return new McpServerResponse(
                dto.serverUid(),
                dto.serverName(),
                dto.transport(),
                dto.status(),
                dto.timeoutSeconds(),
                dto.autoStart(),
                dto.endpoint(),
                dto.headers(),
                dto.command(),
                dto.args(),
                dto.env(),
                dto.cwd(),
                dto.lastConnectedTime(),
                dto.lastError(),
                dto.toolCount(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static List<McpToolResponse> toMcpTools(List<McpToolDto> dtos) {
        return dtos.stream().map(McpApiMapper::toMcpTool).toList();
    }

    public static McpToolResponse toMcpTool(McpToolDto dto) {
        return new McpToolResponse(
                dto.toolKey(),
                dto.serverUid(),
                dto.originalToolName(),
                dto.displayName(),
                dto.description(),
                dto.inputSchemaJson(),
                dto.status(),
                dto.lastSyncedTime()
        );
    }
}
