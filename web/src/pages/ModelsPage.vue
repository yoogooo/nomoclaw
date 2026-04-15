<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  NButton,
  NCard,
  NDrawer,
  NDrawerContent,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NTag
} from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { modelApi } from "@/api/modelApi";
import { message } from "@/discrete";
import { useModelGateStore } from "@/stores/modelGate";
import type { ModelCatalogStatus, ModelConfig, ModelProvider, ModelProviderOption } from "@/types/api";

type ProviderStatusType = "success" | "warning";
const { t } = useI18n();

const loading = ref(false);
const saving = ref(false);
const loadingLocalModels = ref(false);
const testingProviderConnection = ref(false);
const refreshingCatalog = ref(false);
const showEditor = ref(false);
const editingProviderId = ref("");
const modelGateStore = useModelGateStore();

const config = reactive<ModelConfig>({ providers: [] });
const draft = reactive<ModelConfig>({ providers: [] });
const catalogStatus = ref<ModelCatalogStatus | null>(null);
const modelIdInputRefs = ref<Array<{ focus: () => void } | null>>([]);
const modelCardRefs = ref<Array<HTMLElement | null>>([]);

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
      maxFileBytes: Number.isFinite(model.uploadPolicy?.maxFileBytes) ? model.uploadPolicy!.maxFileBytes : 0,
      maxTotalBytes: Number.isFinite(model.uploadPolicy?.maxTotalBytes) ? model.uploadPolicy!.maxTotalBytes : 0,
      singleMimeGroupOnly: Boolean(model.uploadPolicy?.singleMimeGroupOnly),
      allowMixedImageAndFile: Boolean(model.uploadPolicy?.allowMixedImageAndFile)
    },
    catalogMatched: Boolean(model.catalogMatched),
    catalogSource: model.catalogSource ?? ""
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
    const [data, status] = await Promise.all([
      modelApi.getModelConfig(),
      modelApi.getModelCatalogStatus()
    ]);
    config.providers = data.providers.map(normalizeProvider);
    catalogStatus.value = status;
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

async function refreshModelCatalog() {
  refreshingCatalog.value = true;
  try {
    catalogStatus.value = await modelApi.refreshModelCatalog();
    await loadConfig();
    message.success(catalogStatus.value.message || t("models.toast.catalogRefreshed"));
  } catch (error) {
    message.error(error instanceof Error ? error.message : t("toast.refreshFailed"));
  } finally {
    refreshingCatalog.value = false;
  }
}

function openEditor(providerId: string) {
  editingProviderId.value = providerId;
  draft.providers = cloneConfig(config).providers.map(normalizeProvider);
  showEditor.value = true;
}

async function addModel() {
  const provider = editingProvider.value;
  if (!provider) {
    return;
  }
  provider.models.push({
    id: "",
    name: "",
    capabilities: [],
    reasoning: false,
    contextWindow: 0,
    maxInputTokens: 0,
    maxOutputTokens: 0,
    uploadPolicy: {
      enabled: false,
      allowedMimeGroups: [],
      maxFilesPerMessage: 0,
      maxImagesPerMessage: 0,
      maxFileBytes: 0,
      maxTotalBytes: 0,
      singleMimeGroupOnly: false,
      allowMixedImageAndFile: false
    },
    catalogMatched: false,
    catalogSource: ""
  });
  const nextIndex = provider.models.length - 1;
  await nextTick();
  modelCardRefs.value[nextIndex]?.scrollIntoView({ block: "center", behavior: "smooth" });
  modelIdInputRefs.value[nextIndex]?.focus();
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
      id: item.id.trim(),
      name: item.name.trim() || item.id.trim(),
      capabilities: [],
      reasoning: false,
      contextWindow: 0,
      maxInputTokens: 0,
      maxOutputTokens: 0,
      uploadPolicy: {
        enabled: false,
        allowedMimeGroups: [],
        maxFilesPerMessage: 0,
        maxImagesPerMessage: 0,
        maxFileBytes: 0,
        maxTotalBytes: 0,
        singleMimeGroupOnly: false,
        allowMixedImageAndFile: false
      },
      catalogMatched: false,
      catalogSource: "request"
    }));
    provider.defaultModel = provider.defaultModel.trim();
    validateProvider(provider);
    const saved = await modelApi.updateModelConfig(cloneConfig(draft));
    config.providers = saved.providers.map(normalizeProvider);
    await modelGateStore.refreshModelReadiness();
    showEditor.value = false;
    message.success(t("models.toast.saved"));
  } catch (error) {
    message.error(error instanceof Error ? error.message : t("toast.saveFailed"));
  } finally {
    saving.value = false;
  }
}

function modelCapabilityTags(model: ModelProviderOption) {
  const tags: string[] = [];
  if (model.capabilities?.includes("image")) tags.push("Image");
  if (model.capabilities?.includes("pdf")) tags.push("PDF");
  if (model.capabilities?.includes("audio")) tags.push("Audio");
  if (model.capabilities?.includes("video")) tags.push("Video");
  if (!tags.length) tags.push("Text");
  return tags;
}

function uploadSummary(model: ModelProviderOption) {
  const policy = model.uploadPolicy;
  if (!policy?.enabled) {
    return t("models.labels.uploadAutoDisabled");
  }
  const groups = policy.allowedMimeGroups.length ? policy.allowedMimeGroups.join(" / ") : "any";
  return t("models.labels.uploadAutoSummary", {
    types: groups,
    maxImages: policy.maxImagesPerMessage,
    maxFiles: policy.maxFilesPerMessage
  });
}

async function testProviderConnection() {
  const provider = editingProvider.value;
  if (!provider) {
    return;
  }
  testingProviderConnection.value = true;
  try {
    const result = await modelApi.testProviderConnection({
      providerId: provider.id,
      baseUrl: provider.baseUrl.trim(),
      apiKey: provider.apiKey.trim()
    });
    if (result.success) {
      message.success(result.message || t("models.toast.connectionTestSuccess"));
      return;
    }
    message.warning(result.message || t("models.toast.connectionTestFailed"));
  } catch (error) {
    if (!(error instanceof Error)) {
      message.error(t("models.toast.connectionTestFailed"));
    }
  } finally {
    testingProviderConnection.value = false;
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
              <n-button :loading="refreshingCatalog" @click="refreshModelCatalog">{{ t("models.actions.refreshCatalog") }}</n-button>
              <n-button :loading="loading" @click="refreshConfig">{{ t("common.refresh") }}</n-button>
            </template>
          </AppPageHeader>

          <div v-if="catalogStatus" class="catalog-status">
            <n-tag :type="catalogStatus.stale ? 'warning' : 'success'">
              {{ t("models.labels.catalogVersion") }} {{ catalogStatus.catalogVersion || "-" }}
            </n-tag>
            <span>{{ t("models.labels.catalogSource") }}: {{ catalogStatus.source || "-" }}</span>
            <span>{{ t("models.labels.catalogGeneratedAt") }}: {{ catalogStatus.generatedAt || "-" }}</span>
          </div>

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
        <n-form-item label="Base URL">
          <n-input v-model:value="editingProvider.baseUrl" :disabled="editingProvider.freezeUrl" />
        </n-form-item>

        <n-form-item v-if="!editingProvider.local" label="API Key">
          <div class="api-key-test-inline">
            <n-input class="api-key-test-input" v-model:value="editingProvider.apiKey" type="password" show-password-on="click" :placeholder="t('models.editor.apiKeyPlaceholder')" />
            <n-button class="api-key-test-btn" :loading="testingProviderConnection" @click="testProviderConnection">
              {{ t("models.actions.testConnection") }}
            </n-button>
          </div>
        </n-form-item>

        <n-form-item v-else>
          <n-button :loading="testingProviderConnection" @click="testProviderConnection">
            {{ t("models.actions.testConnection") }}
          </n-button>
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
          <section
            v-for="(model, index) in editingProvider.models"
            :key="index"
            :ref="(el) => { modelCardRefs[index] = el as HTMLElement | null; }"
            class="model-card"
          >
            <div class="ui-card-head-between model-card-head">
              <div class="ui-title-strong">{{ t("models.labels.modelIndex", { index: index + 1 }) }}</div>
              <n-button size="tiny" tertiary type="error" @click="removeModel(index)">{{ t("common.delete") }}</n-button>
            </div>
            <n-form-item label="Model ID">
              <n-input
                :ref="(el) => { modelIdInputRefs[index] = el as { focus: () => void } | null; }"
                v-model:value="model.id"
                placeholder="gpt-5.4"
              />
            </n-form-item>
            <n-form-item :label="t('models.labels.displayName')">
              <n-input v-model:value="model.name" placeholder="GPT-5.4" />
            </n-form-item>
            <div class="model-auto-meta">
              <div class="model-auto-row">
                <span>{{ t("models.labels.catalogMatch") }}</span>
                <n-tag :type="model.catalogMatched ? 'success' : 'warning'" size="small">
                  {{ model.catalogMatched ? t("models.labels.catalogMatched") : t("models.labels.catalogUnknown") }}
                </n-tag>
              </div>
              <div class="model-tag-row">
                <n-tag v-for="capability in modelCapabilityTags(model)" :key="capability" size="small">
                  {{ capability }}
                </n-tag>
              </div>
              <div class="model-auto-row">
                <span>{{ t("models.labels.uploadPolicyTitle") }}</span>
                <span>{{ uploadSummary(model) }}</span>
              </div>
              <div class="model-auto-row">
                <span>{{ t("models.labels.catalogSource") }}</span>
                <span>{{ model.catalogSource || "-" }}</span>
              </div>
            </div>
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

.catalog-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
}

.provider-card {
  min-height: 250px;
  cursor: pointer;
  border-radius: var(--radius-xl);
}

.provider-name {
  font-size: var(--text-title-sm-size);
  font-weight: 600;
}

.provider-protocol {
  margin-top: var(--space-1_5);
  color: var(--color-text-muted);
  font-size: var(--text-caption-size);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.provider-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
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

.models-toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--space-4) 0;
  padding: var(--space-2) 0;
  background: var(--color-bg-surface);
  border-bottom: var(--size-1) solid var(--color-border-soft);
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

.model-auto-meta {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.model-auto-row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
}

.model-auto-row span:last-child {
  text-align: right;
  word-break: break-word;
}

.model-tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1_5);
}

.provider-form :deep(.n-form-item) {
  margin-bottom: var(--space-2_5);
}

.api-key-test-inline {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
}

.api-key-test-input {
  flex: 1 1 auto;
  min-width: 0;
}

.api-key-test-btn {
  flex: 0 0 auto;
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
