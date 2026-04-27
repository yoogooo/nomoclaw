import type { McpServer, McpTool, SaveMcpServerPayload } from "@/types/api";
import { requestJson } from "@/utils/http";

export const mcpApi = {
  listServers() {
    return requestJson<McpServer[]>("/api/mcp/servers");
  },
  createServer(payload: SaveMcpServerPayload) {
    return requestJson<McpServer>("/api/mcp/servers", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  updateServer(serverUid: string, payload: SaveMcpServerPayload) {
    return requestJson<McpServer>(`/api/mcp/servers/${serverUid}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  deleteServer(serverUid: string) {
    return requestJson<{ status: string }>(`/api/mcp/servers/${serverUid}`, {
      method: "DELETE"
    });
  },
  testServer(serverUid: string) {
    return requestJson<McpServer>(`/api/mcp/servers/${serverUid}/test`, {
      method: "POST"
    });
  },
  refreshTools(serverUid: string) {
    return requestJson<McpTool[]>(`/api/mcp/servers/${serverUid}/refresh-tools`, {
      method: "POST"
    });
  },
  listTools(serverUid: string) {
    return requestJson<McpTool[]>(`/api/mcp/servers/${serverUid}/tools`);
  }
};
