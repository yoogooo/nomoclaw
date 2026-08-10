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
  NCheckbox,
  NSelect,
  NTag
} from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { modelApi } from "@/api/modelApi";
import { message } from "@/discrete";
import { useModelGateStore } from "@/stores/modelGate";
import type { ModelCapabilities, ModelConfig, ModelProvider, ModelProviderOption } from "@/types/api";

type ProviderStatusType = "success" | "warning";
const modelTypeOptions = ["TEXT_GENERATION", "IMAGE_GENERATION", "VIDEO_GENERATION", "AUDIO_GENERATION", "AUDIO_TRANSCRIPTION", "EMBEDDING", "REALTIME"].map((value) => ({ label: value, value }));
const { t } = useI18n();

const loading = ref(false);
const saving = ref(false);
const loadingLocalModels = ref(false);
const testingProviderConnection = ref(false);
const startingCodexLogin = ref(false);
const codexLoginApiUnsupported = ref(false);
const showEditor = ref(false);
const editingProviderId = ref("");
const modelGateStore = useModelGateStore();

const config = reactive<ModelConfig>({ providers: [] });
const draft = reactive<ModelConfig>({ providers: [] });
const modelIdInputRefs = ref<Array<{ focus: () => void } | null>>([]);
const modelCardRefs = ref<Array<HTMLElement | null>>([]);

const providerCards = computed(() =>
  config.providers.map((provider) => {
    const configured = provider.requireApiKey
      ? provider.local
        ? Boolean(provider.baseUrl.trim())
        : Boolean(provider.apiKey.trim())
      : Boolean(provider.configured);
    const isCodexNotConfigured = provider.id === "codex" && !configured;
    return {
      ...provider,
      statusText: configured
        ? t("models.status.configured")
        : isCodexNotConfigured
          ? t("models.status.pendingCodexLogin")
          : !provider.requireApiKey && provider.authMessage
          ? t("models.status.pendingApiKey")
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
    modelType: model.modelType ?? "TEXT_GENERATION",
    capabilities: normalizeCapabilities(model.capabilities),
    contextWindow: Number.isFinite(model.contextWindow) ? model.contextWindow : 0,
    maxInputTokens: Number.isFinite(model.maxInputTokens) ? model.maxInputTokens : 0,
    maxOutputTokens: Number.isFinite(model.maxOutputTokens) ? model.maxOutputTokens : 0,
    catalogMatched: Boolean(model.catalogMatched),
    catalogSource: model.catalogSource ?? ""
  };
}

function normalizeCapabilities(capabilities?: Partial<ModelCapabilities> | null): ModelCapabilities {
  return { toolCalling: Boolean(capabilities?.toolCalling), imageRecognition: Boolean(capabilities?.imageRecognition),
    audioRecognition: Boolean(capabilities?.audioRecognition), videoRecognition: Boolean(capabilities?.videoRecognition), reasoning: Boolean(capabilities?.reasoning) };
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
    configured: Boolean(provider.configured),
    authStatus: provider.authStatus ?? "",
    authMessage: provider.authMessage ?? "",
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

async function addModel() {
  const provider = editingProvider.value;
  if (!provider) {
    return;
  }
  provider.models.push({
    id: "",
    name: "",
    modelType: "TEXT_GENERATION",
    capabilities: normalizeCapabilities(),
    contextWindow: 0,
    maxInputTokens: 0,
    maxOutputTokens: 0,
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
      modelType: item.modelType || "TEXT_GENERATION",
      capabilities: normalizeCapabilities(item.capabilities),
      contextWindow: 0,
      maxInputTokens: 0,
      maxOutputTokens: 0,
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

function isCodexProvider(provider: ModelProvider) {
  return provider.id === "codex";
}

function canShowCodexLoginAction(provider: ModelProvider) {
  return isCodexProvider(provider) && !provider.configured && !codexLoginApiUnsupported.value;
}

async function startCodexLogin() {
  startingCodexLogin.value = true;
  try {
    const result = await modelApi.startCodexLogin();
    if (result.success) {
      message.success(result.message || t("models.toast.codexLoginStarted"));
      return;
    }
    message.warning(result.message || t("models.toast.codexLoginFailed"));
  } catch (error) {
    const text = error instanceof Error ? error.message : "";
    if (text.includes("resource not found")) {
      codexLoginApiUnsupported.value = true;
      message.warning("当前后端版本不支持一键登录，请在终端运行：codex login");
    } else {
      message.error(text || t("models.toast.codexLoginFailed"));
    }
  } finally {
    startingCodexLogin.value = false;
  }
}

async function testProviderConnection() {
  const provider = editingProvider.value;
  if (!provider) {
    return;
  }
  if (isCodexProvider(provider) && !provider.configured) {
    message.warning("Codex 未登录，请先运行 codex login");
    return;
  }
  testingProviderConnection.value = true;
  try {
    const result = await modelApi.testProviderConnection({
      providerId: provider.id,
      baseUrl: provider.baseUrl.trim(),
      apiKey: provider.requireApiKey ? provider.apiKey.trim() : ""
    });
    if (result.success) {
      message.success(result.message || t("models.toast.connectionTestSuccess"));
      return;
    }
    message.warning(result.message || t("models.toast.connectionTestFailed"));
  } catch (error) {
    const text = error instanceof Error ? error.message : "";
    if (text.includes("Codex 未登录")) {
      message.warning("Codex 未登录，请先运行 codex login");
      return;
    }
    message.error(text || t("models.toast.connectionTestFailed"));
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
                <div class="ui-card-title-row">
                  <div class="ui-card-title-main">
                    <div class="provider-name ui-card-title-text">{{ provider.name }}</div>
                    <div class="provider-protocol ui-card-title-meta">{{ provider.protocol }}</div>
                  </div>
                  <n-tag class="ui-status-pill" :type="provider.statusType" round>{{ provider.statusText }}</n-tag>
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
                <n-button
                  v-if="canShowCodexLoginAction(provider)"
                  size="small"
                  type="primary"
                  secondary
                  :loading="startingCodexLogin"
                  @click.stop="startCodexLogin"
                >
                  {{ t("models.actions.codexLogin") }}
                </n-button>
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

        <n-form-item v-if="!editingProvider.local && editingProvider.requireApiKey" label="API Key">
          <div class="api-key-test-inline">
            <n-input class="api-key-test-input" v-model:value="editingProvider.apiKey" type="password" show-password-on="click" :placeholder="t('models.editor.apiKeyPlaceholder')" />
            <n-button class="api-key-test-btn" :loading="testingProviderConnection" @click="testProviderConnection">
              {{ t("models.actions.testConnection") }}
            </n-button>
          </div>
        </n-form-item>

        <n-form-item v-else-if="editingProvider.local">
          <n-button :loading="testingProviderConnection" @click="testProviderConnection">
            {{ t("models.actions.testConnection") }}
          </n-button>
        </n-form-item>

        <n-form-item v-else label="Codex Login">
          <div class="api-key-test-inline">
            <n-tag :type="editingProvider.configured ? 'success' : 'warning'">
              {{ editingProvider.authMessage || (editingProvider.configured ? t("models.status.configured") : t("models.status.pendingApiKey")) }}
            </n-tag>
            <n-button
              v-if="canShowCodexLoginAction(editingProvider)"
              class="api-key-test-btn"
              type="primary"
              secondary
              :loading="startingCodexLogin"
              @click="startCodexLogin"
            >
              {{ t("models.actions.codexLogin") }}
            </n-button>
            <n-button class="api-key-test-btn" :loading="testingProviderConnection" @click="testProviderConnection">
              {{ t("models.actions.testConnection") }}
            </n-button>
          </div>
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
            <n-form-item label="Model Type">
              <n-select v-model:value="model.modelType" :options="modelTypeOptions" />
            </n-form-item>
            <div class="model-capability-section">
              <div class="model-capability-title">支持功能</div>
              <div class="model-capabilities">
                <n-checkbox v-model:checked="model.capabilities.toolCalling">工具调用</n-checkbox>
                <n-checkbox v-model:checked="model.capabilities.imageRecognition">图片识别</n-checkbox>
                <n-checkbox v-model:checked="model.capabilities.audioRecognition">音频识别</n-checkbox>
                <n-checkbox v-model:checked="model.capabilities.videoRecognition">视频识别</n-checkbox>
                <n-checkbox v-model:checked="model.capabilities.reasoning">思考模式</n-checkbox>
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

.provider-card {
  min-height: 250px;
  cursor: pointer;
  border-radius: var(--radius-xl);
}

.provider-card :deep(.n-card-header__main) {
  min-width: 0;
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
  gap: var(--space-2);
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

.model-capability-section {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.model-capability-title {
  color: var(--color-text-primary);
  font-weight: 600;
}

.model-capabilities {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-1);
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
