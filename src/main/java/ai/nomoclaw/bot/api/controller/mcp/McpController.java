package ai.nomoclaw.bot.api.controller.mcp;

import ai.nomoclaw.bot.api.dto.common.response.SimpleResponse;
import ai.nomoclaw.bot.api.dto.mcp.request.SaveMcpServerRequest;
import ai.nomoclaw.bot.api.dto.mcp.request.UpdateMcpServerStatusRequest;
import ai.nomoclaw.bot.api.dto.mcp.response.McpServerResponse;
import ai.nomoclaw.bot.api.dto.mcp.response.McpToolResponse;
import ai.nomoclaw.bot.api.mapper.McpApiMapper;
import ai.nomoclaw.bot.mcp.McpApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP server and tool management endpoints.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class McpController {

    private final McpApplicationService mcpApplicationService;

    public McpController(McpApplicationService mcpApplicationService) {
        this.mcpApplicationService = mcpApplicationService;
    }

    @GetMapping("/mcp/servers")
    public List<McpServerResponse> listMcpServers() {
        log.info("[AgentAPI] listMcpServers");
        return McpApiMapper.toMcpServers(mcpApplicationService.listServers());
    }

    @PostMapping("/mcp/servers")
    public McpServerResponse createMcpServer(@Valid @RequestBody SaveMcpServerRequest request) {
        log.info("[AgentAPI] createMcpServer serverName={} transport={}", request.serverName(), request.transport());
        return McpApiMapper.toMcpServer(mcpApplicationService.createServer(McpApiMapper.toCommand(request)));
    }

    @PutMapping("/mcp/servers/{serverUid}")
    public McpServerResponse updateMcpServer(@PathVariable String serverUid,
                                             @Valid @RequestBody SaveMcpServerRequest request) {
        log.info("[AgentAPI] updateMcpServer serverUid={} serverName={} transport={}", serverUid, request.serverName(), request.transport());
        return McpApiMapper.toMcpServer(mcpApplicationService.updateServer(serverUid, McpApiMapper.toCommand(request)));
    }

    @PatchMapping("/mcp/servers/{serverUid}/status")
    public McpServerResponse updateMcpServerStatus(@PathVariable String serverUid,
                                                   @Valid @RequestBody UpdateMcpServerStatusRequest request) {
        log.info("[AgentAPI] updateMcpServerStatus serverUid={} enabled={}", serverUid, request.enabled());
        return McpApiMapper.toMcpServer(mcpApplicationService.updateServerStatus(serverUid, request.enabled()));
    }

    @DeleteMapping("/mcp/servers/{serverUid}")
    public SimpleResponse deleteMcpServer(@PathVariable String serverUid) {
        log.info("[AgentAPI] deleteMcpServer serverUid={}", serverUid);
        mcpApplicationService.deleteServer(serverUid);
        return new SimpleResponse("deleted");
    }

    @PostMapping("/mcp/servers/{serverUid}/test")
    public McpServerResponse testMcpServer(@PathVariable String serverUid) {
        log.info("[AgentAPI] testMcpServer serverUid={}", serverUid);
        return McpApiMapper.toMcpServer(mcpApplicationService.testServer(serverUid));
    }

    @PostMapping("/mcp/servers/{serverUid}/refresh-tools")
    public List<McpToolResponse> refreshMcpTools(@PathVariable String serverUid) {
        log.info("[AgentAPI] refreshMcpTools serverUid={}", serverUid);
        return McpApiMapper.toMcpTools(mcpApplicationService.refreshTools(serverUid));
    }

    @GetMapping("/mcp/servers/{serverUid}/tools")
    public List<McpToolResponse> listMcpTools(@PathVariable String serverUid) {
        log.info("[AgentAPI] listMcpTools serverUid={}", serverUid);
        return McpApiMapper.toMcpTools(mcpApplicationService.listTools(serverUid));
    }
}
