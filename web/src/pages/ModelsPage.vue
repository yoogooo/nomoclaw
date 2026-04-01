<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  NButton,
  NCard,
  NDivider,
  NDrawer,
  NDrawerContent,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NSelect,
  NSwitch,
  NTag
} from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { modelApi } from "@/api/modelApi";
import { message } from "@/discrete";
import type { ModelConfig, ModelProvider, ModelProviderOption } from "@/types/api";

type ProviderStatusType = "success" | "warning";

const CAPABILITY_OPTIONS = [
  { label: "Text", value: "text" },
  { label: "Image", value: "image" },
  { label: "Audio", value: "audio" },
  { label: "Video", value: "video" }
];

const UPLOAD_MIME_GROUP_OPTIONS = [
  { label: "Image", value: "image" },
  { label: "PDF", value: "pdf" },
  { label: "Text", value: "text" },
  { label: "Audio", value: "audio" },
  { label: "Video", value: "video" },
  { label: "Application", value: "application" }
];

const loading = ref(false);
const saving = ref(false);
const loadingLocalModels = ref(false);
const showEditor = ref(false);
const editingProviderId = ref("");

const config = reactive<ModelConfig>({ providers: [] });
const draft = reactive<ModelConfig>({ providers: [] });

const providerCards = computed(() =>
  config.providers.map((provider) => {
    const configured = provider.local
      ? Boolean(provider.baseUrl.trim())
      : Boolean(provider.apiKey.trim());
    return {
      ...provider,
      statusText: configured ? "已配置" : provider.local ? "待填写本地地址" : "待填写密钥",
      statusType: (configured ? "success" : "warning") as ProviderStatusType
    };
  })
);

const editingProvider = computed(() => draft.providers.find((item) => item.id === editingProviderId.value) ?? null);
const defaultModelOptions = computed(() =>
  (editingProvider.value?.models ?? []).map((item) => ({
    label: item.name || item.id,
    value: item.id
  }))
);

function cloneConfig(source: ModelConfig): ModelConfig {
  return JSON.parse(JSON.stringify(source)) as ModelConfig;
}

function normalizeModel(model: ModelProviderOption): ModelProviderOption {
  return {
    id: model.id ?? "",
    name: model.name ?? "",
    capabilities: Array.isArray(model.capabilities) ? model.capabilities.filter(Boolean) : [],
    reasoning: Boolean(model.reasoning),
    contextWindow: Number.isFinite(model.contextWindow) ? model.contextWindow : 0,
    maxInputTokens: Number.isFinite(model.maxInputTokens) ? model.maxInputTokens : 0,
    maxOutputTokens: Number.isFinite(model.maxOutputTokens) ? model.maxOutputTokens : 0,
    uploadPolicy: {
      enabled: Boolean(model.uploadPolicy?.enabled),
      allowedMimeGroups: Array.isArray(model.uploadPolicy?.allowedMimeGroups) ? model.uploadPolicy?.allowedMimeGroups.filter(Boolean) : [],
      maxFilesPerMessage: Number.isFinite(model.uploadPolicy?.maxFilesPerMessage) ? model.uploadPolicy!.maxFilesPerMessage : 0,
      maxImagesPerMessage: Number.isFinite(model.uploadPolicy?.maxImagesPerMessage) ? model.uploadPolicy!.maxImagesPerMessage : 0,
      singleMimeGroupOnly: Boolean(model.uploadPolicy?.singleMimeGroupOnly),
      allowMixedImageAndFile: Boolean(model.uploadPolicy?.allowMixedImageAndFile)
    }
  };
}

function normalizeProvider(provider: ModelProvider): ModelProvider {
  return {
    id: provider.id ?? "",
    name: provider.name ?? "",
    protocol: provider.protocol ?? "",
    local: Boolean(provider.local),
    requireApiKey: Boolean(provider.requireApiKey),
    freezeUrl: Boolean(provider.freezeUrl),
    baseUrl: provider.baseUrl ?? "",
    apiKey: provider.apiKey ?? "",
    defaultModel: provider.defaultModel ?? "",
    models: Array.isArray(provider.models) ? provider.models.map(normalizeModel) : []
  };
}

async function loadConfig() {
  loading.value = true;
  try {
    const data = await modelApi.getModelConfig();
    config.providers = data.providers.map(normalizeProvider);
  } finally {
    loading.value = false;
  }
}

async function refreshConfig() {
  try {
    await loadConfig();
    message.success("配置已刷新");
  } catch (error) {
    const text = error instanceof Error ? error.message : "刷新失败";
    message.error(text);
  }
}

function openEditor(providerId: string) {
  editingProviderId.value = providerId;
  draft.providers = cloneConfig(config).providers.map(normalizeProvider);
  showEditor.value = true;
}

function addModel() {
  const provider = editingProvider.value;
  if (!provider) {
    return;
  }
  provider.models.push({
    id: "",
    name: "",
    capabilities: ["text"],
    reasoning: false,
    contextWindow: 0,
    maxInputTokens: 0,
    maxOutputTokens: 0,
    uploadPolicy: {
      enabled: false,
      allowedMimeGroups: [],
      maxFilesPerMessage: 0,
      maxImagesPerMessage: 0,
      singleMimeGroupOnly: false,
      allowMixedImageAndFile: false
    }
  });
}

function removeModel(index: number) {
  const provider = editingProvider.value;
  if (!provider) {
    return;
  }
  const removed = provider.models[index];
  provider.models.splice(index, 1);
  if (provider.defaultModel === removed?.id) {
    provider.defaultModel = provider.models[0]?.id ?? "";
  }
}

function validateProvider(provider: ModelProvider) {
  if (!provider.baseUrl.trim()) {
    throw new Error(`${provider.name} 需要填写 Base URL`);
  }
  if (!provider.local && provider.requireApiKey && !provider.apiKey.trim()) {
    throw new Error(`${provider.name} 需要填写 API Key`);
  }
  if (provider.models.some((model) => !model.id.trim())) {
    throw new Error(`${provider.name} 存在未填写模型 ID 的条目`);
  }
  if (provider.defaultModel.trim() && !provider.models.some((model) => model.id === provider.defaultModel.trim())) {
    throw new Error(`${provider.name} 的默认模型不在模型列表中`);
  }
}

async function saveEditor() {
  const provider = editingProvider.value;
  if (!provider) {
    return;
  }
  saving.value = true;
  try {
    provider.models = provider.models.map((item) => ({
      ...item,
      id: item.id.trim(),
      name: item.name.trim() || item.id.trim(),
      capabilities: Array.from(new Set(item.capabilities.map((capability) => capability.trim().toLowerCase()).filter(Boolean))),
      contextWindow: Math.max(0, item.contextWindow ?? 0),
      maxInputTokens: Math.max(0, item.maxInputTokens ?? 0),
      maxOutputTokens: Math.max(0, item.maxOutputTokens ?? 0),
      uploadPolicy: {
        enabled: Boolean(item.uploadPolicy?.enabled),
        allowedMimeGroups: Array.from(new Set((item.uploadPolicy?.allowedMimeGroups ?? []).map((mimeGroup) => mimeGroup.trim().toLowerCase()).filter(Boolean))),
        maxFilesPerMessage: Math.max(0, item.uploadPolicy?.maxFilesPerMessage ?? 0),
        maxImagesPerMessage: Math.max(0, item.uploadPolicy?.maxImagesPerMessage ?? 0),
        singleMimeGroupOnly: Boolean(item.uploadPolicy?.singleMimeGroupOnly),
        allowMixedImageAndFile: Boolean(item.uploadPolicy?.allowMixedImageAndFile)
      }
    }));
    provider.defaultModel = provider.defaultModel.trim();
    validateProvider(provider);
    const saved = await modelApi.updateModelConfig(cloneConfig(draft));
    config.providers = saved.providers.map(normalizeProvider);
    showEditor.value = false;
    message.success("模型配置已保存");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function loadOllamaLocalModels() {
  const provider = editingProvider.value;
  if (!provider || provider.id !== "ollama") {
    return;
  }
  loadingLocalModels.value = true;
  try {
    const data = await modelApi.loadLocalModels(provider.id);
    config.providers = data.providers.map(normalizeProvider);
    draft.providers = cloneConfig(config).providers.map(normalizeProvider);
    editingProviderId.value = provider.id;
    message.success("已加载本地 Ollama 模型");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "加载本地模型失败");
  } finally {
    loadingLocalModels.value = false;
  }
}

onMounted(() => {
  void loadConfig();
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content models-page">
          <AppPageHeader
            title="模型管理"
            subtitle="模型配置已切到数据库持久化。页面编辑的 provider、默认模型和模型能力信息会直接写入 MySQL。"
          >
            <template #actions>
              <n-button :loading="loading" @click="refreshConfig">刷新配置</n-button>
            </template>
          </AppPageHeader>

          <section class="provider-grid">
            <n-card
              v-for="provider in providerCards"
              :key="provider.id"
              class="provider-card"
              hoverable
              @click="openEditor(provider.id)"
            >
              <template #header>
                <div class="ui-card-head-between-top">
                  <div>
                    <div class="provider-name">{{ provider.name }}</div>
                    <div class="provider-protocol">{{ provider.protocol }}</div>
                  </div>
                  <n-tag :type="provider.statusType" round>{{ provider.statusText }}</n-tag>
                </div>
              </template>

              <div class="provider-meta">
                <span class="meta-label">Base URL</span>
                <span>{{ provider.baseUrl }}</span>
              </div>
              <div class="provider-meta">
                <span class="meta-label">默认模型</span>
                <span>{{ provider.defaultModel || "未设置" }}</span>
              </div>
              <div class="provider-meta">
                <span class="meta-label">模型数量</span>
                <span>{{ provider.models.length }}</span>
              </div>
              <div class="provider-meta">
                <span class="meta-label">URL 策略</span>
                <span>{{ provider.freezeUrl ? "固定官方端点" : "允许自定义" }}</span>
              </div>

              <div class="provider-card-footer">
                <n-button size="small" tertiary type="primary">编辑配置</n-button>
              </div>
            </n-card>
          </section>

        </div>
      </main>
    </div>
  </div>

  <n-drawer v-model:show="showEditor" :width="720" placement="right">
    <n-drawer-content :title="editingProvider?.name || '编辑模型 Provider'" closable>
      <n-form v-if="editingProvider" label-placement="top" class="provider-form">
          <div class="editor-summary">
            <n-tag size="small" :bordered="false">{{ editingProvider.protocol }}</n-tag>
            <span class="editor-summary-text">
              {{ editingProvider.freezeUrl ? "该 Provider 使用平台官方地址，当前页面不支持修改。" : "该 Provider 支持自定义 Base URL。" }}
            </span>
          </div>

        <n-form-item label="Base URL">
          <n-input v-model:value="editingProvider.baseUrl" :disabled="editingProvider.freezeUrl" />
        </n-form-item>

        <n-form-item v-if="!editingProvider.local" label="API Key">
          <n-input v-model:value="editingProvider.apiKey" type="password" show-password-on="click" placeholder="输入 API Key" />
        </n-form-item>

        <n-form-item label="默认模型">
          <n-select
            v-model:value="editingProvider.defaultModel"
            :options="defaultModelOptions"
            clearable
            filterable
            placeholder="选择默认模型"
          />
        </n-form-item>

        <div class="models-toolbar">
          <div class="ui-title-lg">模型列表</div>
          <div class="models-toolbar-actions">
            <n-button
              v-if="editingProvider.id === 'ollama'"
              size="small"
              :loading="loadingLocalModels"
              @click="loadOllamaLocalModels"
            >
              加载本地模型
            </n-button>
            <n-button size="small" type="primary" secondary @click="addModel">新增模型</n-button>
          </div>
        </div>

        <div class="model-stack">
          <section v-for="(model, index) in editingProvider.models" :key="`${model.id || 'new'}-${index}`" class="model-card">
            <div class="ui-card-head-between model-card-head">
              <div class="ui-title-strong">模型 {{ index + 1 }}</div>
              <n-button size="tiny" tertiary type="error" @click="removeModel(index)">删除</n-button>
            </div>
            <n-form-item label="Model ID">
              <n-input v-model:value="model.id" placeholder="gpt-5.4" />
            </n-form-item>
            <n-form-item label="展示名称">
              <n-input v-model:value="model.name" placeholder="GPT-5.4" />
            </n-form-item>
            <n-form-item label="Capabilities">
              <n-select v-model:value="model.capabilities" multiple filterable tag :options="CAPABILITY_OPTIONS" placeholder="选择能力" />
            </n-form-item>
            <div class="model-flags">
              <n-form-item label="Reasoning">
                <n-switch v-model:value="model.reasoning" />
              </n-form-item>
            </div>
            <div class="model-metrics-row">
              <n-form-item label="Context Window (K)">
                <n-input-number v-model:value="model.contextWindow" :min="0" />
              </n-form-item>
              <n-form-item label="Max Input Tokens (K)">
                <n-input-number v-model:value="model.maxInputTokens" :min="0" />
              </n-form-item>
              <n-form-item label="Max Output Tokens (K)">
                <n-input-number v-model:value="model.maxOutputTokens" :min="0" />
              </n-form-item>
            </div>
            <div class="ui-title-sm">文件上传策略</div>
            <div class="model-flags">
              <n-form-item label="允许上传">
                <n-switch v-model:value="model.uploadPolicy!.enabled" />
              </n-form-item>
              <n-form-item label="仅允许单一类型">
                <n-switch v-model:value="model.uploadPolicy!.singleMimeGroupOnly" />
              </n-form-item>
              <n-form-item label="允许图片与文件混传">
                <n-switch v-model:value="model.uploadPolicy!.allowMixedImageAndFile" />
              </n-form-item>
            </div>
            <n-form-item label="允许的文件类型">
              <n-select
                v-model:value="model.uploadPolicy!.allowedMimeGroups"
                multiple
                filterable
                :options="UPLOAD_MIME_GROUP_OPTIONS"
                placeholder="例如 image / pdf / text"
              />
            </n-form-item>
            <div class="model-metrics-row">
              <n-form-item label="非图片文件上限">
                <n-input-number v-model:value="model.uploadPolicy!.maxFilesPerMessage" :min="0" />
              </n-form-item>
              <n-form-item label="图片上限">
                <n-input-number v-model:value="model.uploadPolicy!.maxImagesPerMessage" :min="0" />
              </n-form-item>
            </div>
            <n-divider />
          </section>
        </div>
      </n-form>

      <template #footer>
        <div class="ui-actions-end">
          <n-button @click="showEditor = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveEditor">保存</n-button>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.models-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-4);
}

.provider-card {
  min-height: 250px;
  cursor: pointer;
  border-radius: var(--radius-xl);
}

.provider-name {
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.provider-protocol {
  margin-top: var(--space-1_5);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.provider-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.provider-meta span:last-child {
  max-width: 62%;
  text-align: right;
  word-break: break-word;
}

.provider-card-footer {
  display: flex;
  justify-content: flex-end;
}

.provider-card-footer {
  margin-top: var(--space-5);
}

.editor-summary {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.editor-summary-text {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.models-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--space-4) 0;
}

.models-toolbar-actions {
  display: flex;
  gap: var(--space-2);
}

.model-stack {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.model-card {
  padding: var(--space-3_5);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface-soft);
}

.model-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-2);
}

.model-flags {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-2_5);
}

.model-metrics-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2_5);
}

.provider-form :deep(.n-form-item) {
  margin-bottom: var(--space-2_5);
}

.model-card :deep(.n-form-item) {
  margin-bottom: var(--space-2);
}

.model-card :deep(.n-divider) {
  margin: var(--space-2) 0 0;
}

@media (max-width: 720px) {
  .provider-grid,
  .model-metrics-row {
    grid-template-columns: 1fr;
  }
}
</style>
