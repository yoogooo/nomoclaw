<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
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
const { t } = useI18n();

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
      statusText: configured
        ? t("models.status.configured")
        : provider.local
          ? t("models.status.pendingLocalUrl")
          : t("models.status.pendingApiKey"),
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
    message.success(t("toast.configRefreshed"));
  } catch (error) {
    const text = error instanceof Error ? error.message : t("toast.refreshFailed");
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
    throw new Error(t("models.errors.baseUrlRequired", { name: provider.name }));
  }
  if (!provider.local && provider.requireApiKey && !provider.apiKey.trim()) {
    throw new Error(t("models.errors.apiKeyRequired", { name: provider.name }));
  }
  if (provider.models.some((model) => !model.id.trim())) {
    throw new Error(t("models.errors.modelIdRequired", { name: provider.name }));
  }
  if (provider.defaultModel.trim() && !provider.models.some((model) => model.id === provider.defaultModel.trim())) {
    throw new Error(t("models.errors.defaultModelNotInList", { name: provider.name }));
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
    message.success(t("models.toast.saved"));
  } catch (error) {
    message.error(error instanceof Error ? error.message : t("toast.saveFailed"));
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
    message.success(t("models.toast.localModelsLoaded"));
  } catch (error) {
    message.error(error instanceof Error ? error.message : t("models.toast.localModelsLoadFailed"));
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
            :title="t('pages.models.title')"
            :subtitle="t('pages.models.subtitle')"
          >
            <template #actions>
              <n-button :loading="loading" @click="refreshConfig">{{ t("common.refresh") }}</n-button>
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
                <span class="meta-label">{{ t("models.labels.defaultModel") }}</span>
                <span>{{ provider.defaultModel || t("models.labels.notSet") }}</span>
              </div>
              <div class="provider-meta">
                <span class="meta-label">{{ t("models.labels.modelCount") }}</span>
                <span>{{ provider.models.length }}</span>
              </div>
              <div class="provider-meta">
                <span class="meta-label">{{ t("models.labels.urlPolicy") }}</span>
                <span>{{ provider.freezeUrl ? t("models.labels.urlPolicyFixed") : t("models.labels.urlPolicyCustom") }}</span>
              </div>

              <div class="provider-card-footer">
                <n-button size="small" tertiary type="primary">{{ t("models.actions.editConfig") }}</n-button>
              </div>
            </n-card>
          </section>

        </div>
      </main>
    </div>
  </div>

  <n-drawer v-model:show="showEditor" :width="720" placement="right">
    <n-drawer-content :title="editingProvider?.name || t('models.editor.defaultTitle')" closable>
      <n-form v-if="editingProvider" label-placement="top" class="provider-form">
          <div class="editor-summary">
            <n-tag size="small" :bordered="false">{{ editingProvider.protocol }}</n-tag>
            <span class="editor-summary-text">
              {{ editingProvider.freezeUrl ? t("models.editor.freezeUrlHint") : t("models.editor.customUrlHint") }}
            </span>
          </div>

        <n-form-item label="Base URL">
          <n-input v-model:value="editingProvider.baseUrl" :disabled="editingProvider.freezeUrl" />
        </n-form-item>

        <n-form-item v-if="!editingProvider.local" label="API Key">
          <n-input v-model:value="editingProvider.apiKey" type="password" show-password-on="click" :placeholder="t('models.editor.apiKeyPlaceholder')" />
        </n-form-item>

        <n-form-item :label="t('models.labels.defaultModel')">
          <n-select
            v-model:value="editingProvider.defaultModel"
            :options="defaultModelOptions"
            clearable
            filterable
            :placeholder="t('models.editor.defaultModelPlaceholder')"
          />
        </n-form-item>

        <div class="models-toolbar">
          <div class="ui-title-lg">{{ t("models.labels.modelList") }}</div>
          <div class="models-toolbar-actions">
            <n-button
              v-if="editingProvider.id === 'ollama'"
              size="small"
              :loading="loadingLocalModels"
              @click="loadOllamaLocalModels"
            >
              {{ t("models.actions.loadLocalModels") }}
            </n-button>
            <n-button size="small" type="primary" secondary @click="addModel">{{ t("models.actions.addModel") }}</n-button>
          </div>
        </div>

        <div class="model-stack">
          <section v-for="(model, index) in editingProvider.models" :key="`${model.id || 'new'}-${index}`" class="model-card">
            <div class="ui-card-head-between model-card-head">
              <div class="ui-title-strong">{{ t("models.labels.modelIndex", { index: index + 1 }) }}</div>
              <n-button size="tiny" tertiary type="error" @click="removeModel(index)">{{ t("common.delete") }}</n-button>
            </div>
            <n-form-item label="Model ID">
              <n-input v-model:value="model.id" placeholder="gpt-5.4" />
            </n-form-item>
            <n-form-item :label="t('models.labels.displayName')">
              <n-input v-model:value="model.name" placeholder="GPT-5.4" />
            </n-form-item>
            <n-form-item label="Capabilities">
              <n-select v-model:value="model.capabilities" multiple filterable tag :options="CAPABILITY_OPTIONS" :placeholder="t('models.editor.capabilitiesPlaceholder')" />
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
            <div class="ui-title-sm">{{ t("models.labels.uploadPolicyTitle") }}</div>
            <div class="model-flags">
              <n-form-item :label="t('models.labels.uploadEnabled')">
                <n-switch v-model:value="model.uploadPolicy!.enabled" />
              </n-form-item>
              <n-form-item :label="t('models.labels.singleMimeGroupOnly')">
                <n-switch v-model:value="model.uploadPolicy!.singleMimeGroupOnly" />
              </n-form-item>
              <n-form-item :label="t('models.labels.allowMixedImageAndFile')">
                <n-switch v-model:value="model.uploadPolicy!.allowMixedImageAndFile" />
              </n-form-item>
            </div>
            <n-form-item :label="t('models.labels.allowedFileTypes')">
              <n-select
                v-model:value="model.uploadPolicy!.allowedMimeGroups"
                multiple
                filterable
                :options="UPLOAD_MIME_GROUP_OPTIONS"
                :placeholder="t('models.editor.allowedFileTypesPlaceholder')"
              />
            </n-form-item>
            <div class="model-metrics-row">
              <n-form-item :label="t('models.labels.maxFilesPerMessage')">
                <n-input-number v-model:value="model.uploadPolicy!.maxFilesPerMessage" :min="0" />
              </n-form-item>
              <n-form-item :label="t('models.labels.maxImagesPerMessage')">
                <n-input-number v-model:value="model.uploadPolicy!.maxImagesPerMessage" :min="0" />
              </n-form-item>
            </div>
            <n-divider />
          </section>
        </div>
      </n-form>

      <template #footer>
        <div class="ui-actions-end">
          <n-button @click="showEditor = false">{{ t("common.cancel") }}</n-button>
          <n-button type="primary" :loading="saving" @click="saveEditor">{{ t("common.save") }}</n-button>
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
