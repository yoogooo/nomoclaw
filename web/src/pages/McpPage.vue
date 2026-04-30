<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NPopconfirm,
  NSwitch,
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

const { t } = useI18n();
const loading = ref(false);
const saving = ref(false);
const testingUid = ref("");
const refreshingUid = ref("");
const statusUpdatingUid = ref("");
const showEditor = ref(false);
const showToolsModal = ref(false);
const editingUid = ref("");
const viewingToolsServerName = ref("");
const showToolParamsModal = ref(false);
const viewingToolName = ref("");
const viewingToolParamsText = ref("");
const activeTab = ref<TransportTab>("HTTP");
const servers = ref<McpServer[]>([]);
const tools = ref<McpTool[]>([]);
const toolsLoading = ref(false);

const form = reactive({
  serverName: "",
  timeoutSeconds: 30,
  autoStart: true,
  endpoint: "",
  bearerTokenEnvVar: "",
  headers: [{ key: "", value: "" }],
  command: "",
  args: [""],
  env: [{ key: "", value: "" }],
  cwd: ""
});

const serverColumns = computed<DataTableColumns<McpServer>>(() => [
  {
    title: () => h("span", { class: "mcp-name-header" }, t("mcp.columns.name")),
    key: "serverName",
    width: 180,
    minWidth: 180,
    render(row) {
      return h("span", { class: "mcp-name-value" }, row.serverName);
    }
  },
  {
    title: t("mcp.columns.transport"),
    key: "transport",
    width: 120,
    minWidth: 120,
    render(row) {
      return row.transport;
    }
  },
  {
    title: t("mcp.columns.status"),
    key: "status",
    width: 96,
    minWidth: 96,
    render(row) {
      return h(NSwitch, {
        value: row.status === "ACTIVE",
        loading: statusUpdatingUid.value === row.serverUid,
        size: "small",
        onUpdateValue: (enabled: boolean) => updateServerStatus(row, enabled)
      });
    }
  },
  { title: t("mcp.columns.tools"), key: "toolCount", width: 88, minWidth: 88 },
  {
    title: t("mcp.columns.actions"),
    key: "actions",
    width: 300,
    minWidth: 300,
    render(row) {
      return h("div", { class: "mcp-actions" }, [
        hButton(t("common.edit"), () => openEditor(row)),
        hButton(t("mcp.actions.test"), () => testServer(row.serverUid), testingUid.value === row.serverUid),
        hButton(t("mcp.actions.refreshTools"), () => refreshTools(row.serverUid), refreshingUid.value === row.serverUid),
        hButton(t("mcp.actions.viewTools"), () => openTools(row)),
        h(
          NPopconfirm,
          {
            positiveText: t("common.delete"),
            negativeText: t("common.cancel"),
            positiveButtonProps: { type: "error" },
            onPositiveClick: () => deleteServer(row.serverUid)
          },
          {
            trigger: () => hButton(t("common.delete"), () => undefined),
            default: () => t("mcp.confirmDelete", { name: row.serverName })
          }
        )
      ]);
    }
  }
]);

const toolColumns = computed<DataTableColumns<McpTool>>(() => [
  {
    title: t("mcp.toolColumns.name"),
    key: "originalToolName",
    minWidth: 240
  },
  { title: t("mcp.toolColumns.description"), key: "description" },
  {
    title: t("mcp.toolColumns.params"),
    key: "params",
    width: 120,
    render(row) {
      return h(
        NButton,
        {
          size: "small",
          tertiary: true,
          onClick: () => openToolParams(row)
        },
        { default: () => t("mcp.actions.viewParams") }
      );
    }
  }
]);

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
    timeoutSeconds: 30,
    autoStart: true,
    endpoint: "",
    bearerTokenEnvVar: "",
    headers: [{ key: "", value: "" }],
    command: "",
    args: [""],
    env: [{ key: "", value: "" }],
    cwd: ""
  });
  showEditor.value = true;
}

function openEditor(server: McpServer) {
  editingUid.value = server.serverUid;
  activeTab.value = server.transport;
  Object.assign(form, {
    serverName: server.serverName,
    timeoutSeconds: server.timeoutSeconds || 30,
    autoStart: Boolean(server.autoStart),
    endpoint: server.endpoint || "",
    bearerTokenEnvVar: bearerTokenEnvVarFromHeaders(server.headers),
    headers: mapToRows(server.headers, ["Authorization"]),
    command: server.command || "",
    args: server.args?.length ? [...server.args] : [""],
    env: mapToRows(server.env),
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
      message.success(t("mcp.toast.updated"));
    } else {
      await mcpApi.createServer(payload);
      message.success(t("mcp.toast.created"));
    }
    showEditor.value = false;
    await loadServers();
  } catch (error) {
    const text = error instanceof Error ? error.message : t("mcp.toast.saveFailed");
    message.error(text);
  } finally {
    saving.value = false;
  }
}

async function testServer(serverUid: string) {
  testingUid.value = serverUid;
  try {
    await mcpApi.testServer(serverUid);
    message.success(t("mcp.toast.testSuccess"));
    await loadServers();
  } finally {
    testingUid.value = "";
  }
}

async function refreshTools(serverUid: string) {
  refreshingUid.value = serverUid;
  try {
    tools.value = await mcpApi.refreshTools(serverUid);
    const server = servers.value.find((item) => item.serverUid === serverUid);
    viewingToolsServerName.value = server?.serverName || "";
    showToolsModal.value = true;
    message.success(t("mcp.toast.toolsRefreshed"));
    await loadServers();
  } finally {
    refreshingUid.value = "";
  }
}

async function openTools(server: McpServer) {
  viewingToolsServerName.value = server.serverName;
  showToolsModal.value = true;
  toolsLoading.value = true;
  try {
    tools.value = await mcpApi.listTools(server.serverUid);
  } finally {
    toolsLoading.value = false;
  }
}

function openToolParams(tool: McpTool) {
  viewingToolName.value = tool.originalToolName;
  viewingToolParamsText.value = formatToolParams(tool.inputSchemaJson);
  showToolParamsModal.value = true;
}

function formatToolParams(inputSchemaJson?: string) {
  if (!inputSchemaJson || !inputSchemaJson.trim()) {
    return t("mcp.params.empty");
  }
  try {
    const schema = JSON.parse(inputSchemaJson) as {
      properties?: Record<string, { type?: string; description?: string; enum?: unknown[] }>;
      required?: string[];
    };
    const properties = schema?.properties || {};
    const entries = Object.entries(properties);
    if (!entries.length) {
      return t("mcp.params.empty");
    }
    const required = new Set(schema.required || []);
    return entries
      .map(([name, def]) => {
        const type = normalizeParamType(def);
        const requiredText = required.has(name) ? t("mcp.params.required") : t("mcp.params.optional");
        const desc = (def?.description || "").trim() || t("mcp.params.noDescription");
        return `- ${name} (${type}, ${requiredText}): ${desc}`;
      })
      .join("\n");
  } catch {
    return t("mcp.params.invalid");
  }
}

function normalizeParamType(def: { type?: string; enum?: unknown[] } | undefined) {
  if (!def) return t("mcp.params.unknownType");
  if (typeof def.type === "string" && def.type.trim()) {
    return def.type.trim();
  }
  if (Array.isArray(def.enum) && def.enum.length > 0) {
    return `enum(${def.enum.map((item) => String(item)).join(", ")})`;
  }
  return t("mcp.params.unknownType");
}

async function deleteServer(serverUid: string) {
  await mcpApi.deleteServer(serverUid);
  message.success(t("mcp.toast.deleted"));
  await loadServers();
}

async function updateServerStatus(server: McpServer, enabled: boolean) {
  statusUpdatingUid.value = server.serverUid;
  try {
    await mcpApi.updateServerStatus(server.serverUid, enabled);
    message.success(enabled ? t("mcp.toast.enabled") : t("mcp.toast.disabled"));
    await loadServers();
  } finally {
    statusUpdatingUid.value = "";
  }
}

function buildPayload(): SaveMcpServerPayload {
  const serverName = normalizeServerName(form.serverName);
  if (!serverName) {
    throw new Error(t("mcp.toast.nameRequired"));
  }
  if (isDuplicateServerName(serverName)) {
    throw new Error(t("mcp.toast.nameDuplicated"));
  }
  const payload: SaveMcpServerPayload = {
    serverName,
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
    payload.env = rowsToMap(form.env);
    payload.cwd = form.cwd.trim();
  }
  return payload;
}

function normalizeServerName(value: string) {
  return value.trim().slice(0, 100);
}

function isDuplicateServerName(serverName: string) {
  return servers.value.some((server) => server.serverName === serverName && server.serverUid !== editingUid.value);
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

function addEnv() {
  form.env.push({ key: "", value: "" });
}

function removeEnv(index: number) {
  form.env.splice(index, 1);
  if (!form.env.length) {
    form.env.push({ key: "", value: "" });
  }
}

function rowsToMap(rows: Array<{ key: string; value: string }>): Record<string, string> {
  const result: Record<string, string> = {};
  rows.forEach((row) => {
    const key = row.key.trim();
    if (key) result[key] = row.value.trim();
  });
  return result;
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
          <AppPageHeader :title="t('mcp.title')" :subtitle="t('mcp.subtitle')">
            <template #actions>
              <n-button secondary :loading="loading" @click="loadServers">{{ t("common.refresh") }}</n-button>
              <n-button type="primary" @click="openCreate">{{ t("mcp.actions.addServer") }}</n-button>
            </template>
          </AppPageHeader>

          <div class="mcp-grid">
            <n-card :title="t('mcp.serverListTitle')" class="mcp-card">
              <div class="mcp-table-wrap">
                <n-data-table
                  :columns="serverColumns"
                  :data="servers"
                  :loading="loading"
                  :row-key="(row) => row.serverUid"
                  :scroll-x="784"
                  size="small"
                />
              </div>
            </n-card>
          </div>
        </div>
      </main>
    </div>

    <n-modal
      v-model:show="showEditor"
      preset="card"
      class="ui-card-modal-md"
      :title="editingUid ? t('mcp.editor.editTitle') : t('mcp.editor.createTitle')"
    >
      <n-form label-placement="top">
        <n-form-item :label="t('mcp.editor.name')">
          <n-input
            v-model:value="form.serverName"
            :placeholder="t('mcp.editor.namePlaceholder')"
            maxlength="100"
            show-count
          />
        </n-form-item>
        <n-tabs v-model:value="activeTab" type="segment">
          <n-tab-pane name="HTTP" :tab="t('mcp.editor.tabHttp')">
            <n-form-item :label="t('mcp.editor.url')">
              <n-input v-model:value="form.endpoint" placeholder="https://mcp.example.com/mcp" />
            </n-form-item>
            <n-form-item :label="t('mcp.editor.bearerTokenEnvVar')">
              <n-input v-model:value="form.bearerTokenEnvVar" placeholder="MCP_BEARER_TOKEN" />
            </n-form-item>
            <n-form-item :label="t('mcp.editor.headers')">
              <div class="row-editor">
                <div v-for="(row, index) in form.headers" :key="index" class="row-editor-line row-editor-header">
                  <n-input v-model:value="row.key" :placeholder="t('mcp.editor.headerKey')" />
                  <n-input v-model:value="row.value" :placeholder="t('mcp.editor.headerValue')" />
                  <n-button quaternary circle @click="removeHeader(index)">×</n-button>
                </div>
                <n-button block secondary @click="addHeader">＋ {{ t("mcp.editor.addHeader") }}</n-button>
              </div>
            </n-form-item>
          </n-tab-pane>
          <n-tab-pane name="STDIO" :tab="t('mcp.editor.tabStdio')">
            <n-form-item :label="t('mcp.editor.commandToLaunch')">
              <n-input v-model:value="form.command" placeholder="openai-dev-mcp serve-sqlite" />
            </n-form-item>
            <n-form-item :label="t('mcp.editor.arguments')">
              <div class="row-editor">
                <div v-for="(_, index) in form.args" :key="index" class="row-editor-line">
                  <n-input v-model:value="form.args[index]" placeholder="" />
                  <n-button quaternary circle @click="removeArgument(index)">×</n-button>
                </div>
                <n-button block secondary @click="addArgument">＋ {{ t("mcp.editor.addArgument") }}</n-button>
              </div>
            </n-form-item>
            <n-form-item :label="t('mcp.editor.env')">
              <div class="row-editor">
                <div v-for="(row, index) in form.env" :key="index" class="row-editor-line row-editor-header">
                  <n-input v-model:value="row.key" :placeholder="t('mcp.editor.envKey')" />
                  <n-input v-model:value="row.value" :placeholder="t('mcp.editor.envValue')" />
                  <n-button quaternary circle @click="removeEnv(index)">×</n-button>
                </div>
                <n-button block secondary @click="addEnv">＋ {{ t("mcp.editor.addEnv") }}</n-button>
              </div>
            </n-form-item>
            <n-form-item :label="t('mcp.editor.cwd')">
              <n-input v-model:value="form.cwd" placeholder="/path/to/workdir" />
            </n-form-item>
          </n-tab-pane>
        </n-tabs>
      </n-form>

      <template #footer>
        <div class="modal-actions">
          <n-button @click="showEditor = false">{{ t("common.cancel") }}</n-button>
          <n-button type="primary" :loading="saving" @click="saveServer">{{ t("common.save") }}</n-button>
        </div>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showToolsModal"
      preset="card"
      class="ui-card-modal-lg ui-card-modal-max-90"
      :title="t('mcp.toolsModal.title', { name: viewingToolsServerName })"
    >
      <div class="mcp-tools-table-wrap">
        <n-data-table
          :columns="toolColumns"
          :data="tools"
          :loading="toolsLoading"
          :row-key="(row) => row.toolKey"
          size="small"
        />
      </div>
    </n-modal>

    <n-modal
      v-model:show="showToolParamsModal"
      preset="card"
      class="ui-card-modal-md"
      :title="t('mcp.toolsModal.paramsTitle', { name: viewingToolName })"
    >
      <pre class="tool-params-text">{{ viewingToolParamsText }}</pre>
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
  min-width: 0;
}

.mcp-table-wrap {
  width: 100%;
  min-width: 0;
  overflow-x: auto;
}

.mcp-table-wrap :deep(.n-data-table) {
  min-width: 720px;
}

.mcp-tools-table-wrap {
  max-height: calc(90vh - 150px);
  overflow: auto;
  min-height: 0;
}

.tool-params-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
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

@media (max-width: 1120px) {
  .mcp-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .mcp-card {
    min-height: 320px;
  }

  :deep(.mcp-name-header),
  :deep(.mcp-name-value) {
    padding-left: 0;
  }
}
</style>
