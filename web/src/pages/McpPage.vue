<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from "vue";
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NPopconfirm,
  NTabPane,
  NTabs,
} from "naive-ui";
import type { DataTableColumns } from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { mcpApi } from "@/api/mcpApi";
import { message } from "@/discrete";
import type { McpServer, McpTool, SaveMcpServerPayload } from "@/types/api";

type TransportTab = "HTTP" | "STDIO";

const loading = ref(false);
const saving = ref(false);
const testingUid = ref("");
const refreshingUid = ref("");
const showEditor = ref(false);
const editingUid = ref("");
const activeTab = ref<TransportTab>("HTTP");
const servers = ref<McpServer[]>([]);

const form = reactive({
  serverName: "",
  displayName: "",
  timeoutSeconds: 30,
  autoStart: true,
  endpoint: "",
  bearerTokenEnvVar: "",
  headers: [{ key: "", value: "" }],
  command: "",
  args: [""],
  envText: "",
  cwd: ""
});

const serverColumns: DataTableColumns<McpServer> = [
  {
    title: () => h("span", { class: "mcp-name-header" }, "名称"),
    key: "displayName",
    render(row) {
      return h("span", { class: "mcp-name-value" }, row.displayName || row.serverName);
    }
  },
  {
    title: "Transport",
    key: "transport",
    render(row) {
      return row.transport;
    }
  },
  {
    title: "状态",
    key: "status",
    render(row) {
      return row.status === "ACTIVE" ? "启用" : "停用";
    }
  },
  { title: "Tools", key: "toolCount" },
  {
    title: "操作",
    key: "actions",
    render(row) {
      return h("div", { class: "mcp-actions" }, [
        hButton("编辑", () => openEditor(row)),
        hButton("测试", () => testServer(row.serverUid), testingUid.value === row.serverUid),
        hButton("刷新工具", () => refreshTools(row.serverUid), refreshingUid.value === row.serverUid),
        h(
          NPopconfirm,
          {
            positiveText: "删除",
            negativeText: "取消",
            positiveButtonProps: { type: "error" },
            onPositiveClick: () => deleteServer(row.serverUid)
          },
          {
            trigger: () => hButton("删除", () => undefined),
            default: () => `确认删除 ${row.displayName || row.serverName}？`
          }
        )
      ]);
    }
  }
];

function hButton(label: string, onClick: () => void, loadingValue = false) {
  return h(
    NButton,
    {
      size: "small",
      tertiary: true,
      loading: loadingValue,
      class: "mcp-action-button",
      onClick
    },
    { default: () => label }
  );
}

async function loadServers() {
  loading.value = true;
  try {
    servers.value = await mcpApi.listServers();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingUid.value = "";
  activeTab.value = "HTTP";
  Object.assign(form, {
    serverName: "",
    displayName: "",
    timeoutSeconds: 30,
    autoStart: true,
    endpoint: "",
    bearerTokenEnvVar: "",
    headers: [{ key: "", value: "" }],
    command: "",
    args: [""],
    envText: "",
    cwd: ""
  });
  showEditor.value = true;
}

function openEditor(server: McpServer) {
  editingUid.value = server.serverUid;
  activeTab.value = server.transport;
  Object.assign(form, {
    serverName: server.serverName,
    displayName: server.displayName,
    timeoutSeconds: server.timeoutSeconds || 30,
    autoStart: Boolean(server.autoStart),
    endpoint: server.endpoint || "",
    bearerTokenEnvVar: bearerTokenEnvVarFromHeaders(server.headers),
    headers: mapToRows(server.headers, ["Authorization"]),
    command: server.command || "",
    args: server.args?.length ? [...server.args] : [""],
    envText: mapToLines(server.env),
    cwd: server.cwd || ""
  });
  showEditor.value = true;
}

async function saveServer() {
  saving.value = true;
  try {
    const payload = buildPayload();
    if (editingUid.value) {
      await mcpApi.updateServer(editingUid.value, payload);
      message.success("MCP Server 已更新");
    } else {
      await mcpApi.createServer(payload);
      message.success("MCP Server 已创建");
    }
    showEditor.value = false;
    await loadServers();
  } catch (error) {
    const text = error instanceof Error ? error.message : "保存失败";
    message.error(text);
  } finally {
    saving.value = false;
  }
}

async function testServer(serverUid: string) {
  testingUid.value = serverUid;
  try {
    await mcpApi.testServer(serverUid);
    message.success("连接测试成功");
    await loadServers();
  } finally {
    testingUid.value = "";
  }
}

async function refreshTools(serverUid: string) {
  refreshingUid.value = serverUid;
  try {
    await mcpApi.refreshTools(serverUid);
    message.success("工具已刷新");
    await loadServers();
  } finally {
    refreshingUid.value = "";
  }
}

async function deleteServer(serverUid: string) {
  await mcpApi.deleteServer(serverUid);
  message.success("MCP Server 已删除");
  await loadServers();
}

function buildPayload(): SaveMcpServerPayload {
  const payload: SaveMcpServerPayload = {
    serverName: form.serverName.trim(),
    displayName: form.displayName.trim(),
    transport: activeTab.value,
    timeoutSeconds: Number(form.timeoutSeconds) || 30,
    autoStart: true
  };
  if (activeTab.value === "HTTP") {
    payload.endpoint = form.endpoint.trim();
    payload.headers = buildHttpHeaders();
  } else {
    payload.command = form.command.trim();
    payload.args = form.args.map((item) => item.trim()).filter(Boolean);
    payload.env = linesToMap(form.envText);
    payload.cwd = form.cwd.trim();
  }
  return payload;
}

function buildHttpHeaders(): Record<string, string> {
  const headers: Record<string, string> = {};
  const bearerEnv = form.bearerTokenEnvVar.trim();
  if (bearerEnv) {
    headers.Authorization = `Bearer \${${bearerEnv}}`;
  }
  form.headers.forEach((row) => {
    const key = row.key.trim();
    if (key) headers[key] = row.value.trim();
  });
  return headers;
}

function addHeader() {
  form.headers.push({ key: "", value: "" });
}

function removeHeader(index: number) {
  form.headers.splice(index, 1);
  if (!form.headers.length) {
    form.headers.push({ key: "", value: "" });
  }
}

function addArgument() {
  form.args.push("");
}

function removeArgument(index: number) {
  form.args.splice(index, 1);
  if (!form.args.length) {
    form.args.push("");
  }
}

function linesToMap(text: string): Record<string, string> {
  const result: Record<string, string> = {};
  text.split("\n").forEach((line) => {
    const index = line.indexOf("=");
    if (index <= 0) return;
    const key = line.slice(0, index).trim();
    const value = line.slice(index + 1).trim();
    if (key) result[key] = value;
  });
  return result;
}

function mapToLines(value: Record<string, string> | undefined) {
  return Object.entries(value || {}).map(([key, val]) => `${key}=${val}`).join("\n");
}

function mapToRows(value: Record<string, string> | undefined, excludeKeys: string[] = []) {
  const excluded = new Set(excludeKeys.map((key) => key.toLowerCase()));
  const rows = Object.entries(value || {})
    .filter(([key]) => !excluded.has(key.toLowerCase()))
    .map(([key, val]) => ({ key, value: val }));
  return rows.length ? rows : [{ key: "", value: "" }];
}

function bearerTokenEnvVarFromHeaders(value: Record<string, string> | undefined) {
  const authorization = Object.entries(value || {}).find(([key]) => key.toLowerCase() === "authorization")?.[1] || "";
  const match = authorization.match(/^Bearer\s+\$\{(.+)}$/);
  return match?.[1] || "";
}

onMounted(() => {
  void loadServers();
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content">
          <AppPageHeader title="MCP 管理" subtitle="管理外部 MCP Server，并将发现出的工具接入 Agent。">
            <template #actions>
              <n-button secondary :loading="loading" @click="loadServers">刷新</n-button>
              <n-button type="primary" @click="openCreate">新增 MCP Server</n-button>
            </template>
          </AppPageHeader>

          <div class="mcp-grid">
            <n-card title="MCP Servers" class="mcp-card">
              <n-data-table
                :columns="serverColumns"
                :data="servers"
                :loading="loading"
                :row-key="(row) => row.serverUid"
                size="small"
              />
            </n-card>
          </div>
        </div>
      </main>
    </div>

    <n-modal
      v-model:show="showEditor"
      preset="card"
      class="mcp-editor-modal"
      :style="{ width: 'clamp(560px, 52vw, 760px)', maxWidth: 'calc(100vw - var(--size-40))' }"
      :title="editingUid ? '编辑 MCP Server' : '新增 MCP Server'"
    >
      <n-form label-placement="top">
        <n-form-item label="Name">
          <n-input v-model:value="form.serverName" placeholder="MCP server name" />
        </n-form-item>
        <n-tabs v-model:value="activeTab" type="segment">
          <n-tab-pane name="HTTP" tab="Streamable HTTP">
            <n-form-item label="URL">
              <n-input v-model:value="form.endpoint" placeholder="https://mcp.example.com/mcp" />
            </n-form-item>
            <n-form-item label="Bearer token env var">
              <n-input v-model:value="form.bearerTokenEnvVar" placeholder="MCP_BEARER_TOKEN" />
            </n-form-item>
            <n-form-item label="Headers">
              <div class="row-editor">
                <div v-for="(row, index) in form.headers" :key="index" class="row-editor-line row-editor-header">
                  <n-input v-model:value="row.key" placeholder="Key" />
                  <n-input v-model:value="row.value" placeholder="Value" />
                  <n-button quaternary circle @click="removeHeader(index)">×</n-button>
                </div>
                <n-button block secondary @click="addHeader">＋ Add header</n-button>
              </div>
            </n-form-item>
          </n-tab-pane>
          <n-tab-pane name="STDIO" tab="STDIO">
            <n-form-item label="Command to launch">
              <n-input v-model:value="form.command" placeholder="openai-dev-mcp serve-sqlite" />
            </n-form-item>
            <n-form-item label="Arguments">
              <div class="row-editor">
                <div v-for="(_, index) in form.args" :key="index" class="row-editor-line">
                  <n-input v-model:value="form.args[index]" placeholder="" />
                  <n-button quaternary circle @click="removeArgument(index)">×</n-button>
                </div>
                <n-button block secondary @click="addArgument">＋ Add argument</n-button>
              </div>
            </n-form-item>
            <n-form-item label="Env（每行 key=value）">
              <n-input v-model:value="form.envText" type="textarea" placeholder="TOKEN=..." />
            </n-form-item>
            <n-form-item label="CWD">
              <n-input v-model:value="form.cwd" placeholder="/path/to/workdir" />
            </n-form-item>
          </n-tab-pane>
        </n-tabs>
      </n-form>

      <template #footer>
        <div class="modal-actions">
          <n-button @click="showEditor = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveServer">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.mcp-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-4);
}

.mcp-card {
  min-height: 420px;
}

.mcp-editor-modal {
  width: clamp(560px, 52vw, 760px);
  max-width: calc(100vw - var(--size-40));
}

:deep(.mcp-editor-modal.n-card) {
  width: clamp(560px, 52vw, 760px) !important;
  max-width: calc(100vw - var(--size-40)) !important;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

:deep(.mcp-actions) {
  display: flex;
  align-items: center;
  min-height: 34px;
  line-height: 1;
  white-space: nowrap;
}

:deep(.mcp-name-header),
:deep(.mcp-name-value) {
  display: inline-block;
  padding-left: var(--space-4);
}

:deep(.mcp-action-button) {
  height: 34px;
  min-width: 80px;
  margin-right: var(--space-2);
  vertical-align: middle;
}

:deep(.mcp-action-button .n-button__content) {
  min-width: 0;
  line-height: 1;
}

:deep(.mcp-action-button .n-button__icon) {
  width: 16px;
  height: 16px;
  margin-right: var(--space-2);
}

.row-editor {
  width: 100%;
  display: grid;
  gap: var(--space-2);
}

.row-editor-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
  align-items: center;
}

.row-editor-header {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
}

@media (max-width: var(--size-breakpoint-lg)) {
  .mcp-grid {
    grid-template-columns: 1fr;
  }

  .mcp-editor-modal {
    width: min(560px, calc(100vw - var(--size-32)));
  }

  :deep(.mcp-editor-modal.n-card) {
    width: min(560px, calc(100vw - var(--size-32))) !important;
  }
}
</style>
