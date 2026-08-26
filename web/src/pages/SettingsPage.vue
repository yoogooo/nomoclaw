<script setup lang="ts">
import { RefreshCw } from "lucide-vue-next";
import { NButton, NIcon, NSelect, NSwitch } from "naive-ui";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { systemDiagnosticsApi } from "@/api/systemDiagnosticsApi";
import { useUiPreferencesStore } from "@/stores/uiPreferences";
import { useUpdateManagerStore } from "@/stores/updateManager";
import type { AppLocale } from "@/i18n";
import type { SystemErrorLogSummary } from "@/types/api";
import { computed, onMounted, ref } from "vue";

const uiPreferencesStore = useUiPreferencesStore();
const updateManagerStore = useUpdateManagerStore();
uiPreferencesStore.init();
const { t } = useI18n();
const router = useRouter();
const diagnosticsSummary = ref<SystemErrorLogSummary>({
  hasErrors: false,
  recent24hCount: 0,
  latestOccurredTime: null
});
const diagnosticsLoading = ref(false);
const localeOptions = computed(() => [
  { label: t("settings.languageZhCN"), value: "zh-CN" },
  { label: t("settings.languageEnUS"), value: "en-US" }
]);
const downloadPercentText = computed(() => {
  const value = updateManagerStore.downloadProgress;
  if (value === null || value === undefined) return "";
  return `${Math.round(Math.min(Math.max(value, 0), 1) * 100)}%`;
});
const diagnosticsHasErrors = computed(() => diagnosticsSummary.value.hasErrors);
onMounted(() => {
  void updateManagerStore.init();
  void loadDiagnosticsSummary();
});

function onLocaleChange(value: AppLocale | null) {
  if (!value) return;
  uiPreferencesStore.setLocale(value);
}

function openDiagnosticsPage() {
  void router.push({ name: "settings-error-logs" });
}

function openTokenUsagePage() {
  void router.push({ name: "settings-token-usage" });
}

async function loadDiagnosticsSummary() {
  diagnosticsLoading.value = true;
  try {
    diagnosticsSummary.value = await systemDiagnosticsApi.getErrorLogSummary();
  } catch {
    diagnosticsSummary.value = {
      hasErrors: false,
      recent24hCount: 0,
      latestOccurredTime: null
    };
  } finally {
    diagnosticsLoading.value = false;
  }
}

</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content">
          <AppPageHeader
            :title="t('settings.title')"
            :subtitle="t('settings.subtitle')"
          >
            <template #actions>
              <div v-if="updateManagerStore.supported" class="settings-updater-actions">
                <span v-if="updateManagerStore.latestVersion" class="settings-updater-version">
                  {{ t("settings.updateVersion", { version: updateManagerStore.latestVersion }) }}
                </span>
                <span v-if="updateManagerStore.status === 'downloading'" class="settings-updater-progress">
                  {{ t("settings.updateDownloading", { progress: downloadPercentText || "--" }) }}
                </span>
                <span v-if="updateManagerStore.status === 'error' && updateManagerStore.errorMessage" class="settings-updater-error">
                  {{ t("settings.updateFailedWithReason", { reason: updateManagerStore.errorMessage }) }}
                </span>

                <n-button
                  v-if="updateManagerStore.status === 'downloaded'"
                  type="primary"
                  :loading="updateManagerStore.busy"
                  @click="() => updateManagerStore.installDownloaded()"
                >
                  {{ t("settings.updateInstallNow") }}
                </n-button>
                <n-button
                  v-else
                  tertiary
                  :loading="updateManagerStore.status === 'checking' || updateManagerStore.busy"
                  @click="() => updateManagerStore.checkNow()"
                >
                  {{
                    updateManagerStore.status === "error"
                      ? t("settings.updateRetry")
                      : t("settings.updateCheckNow")
                  }}
                </n-button>
              </div>
            </template>
          </AppPageHeader>
          <div class="page-section-grid settings-grid">
            <section class="surface-card">
              <div class="surface-card-title">{{ t("settings.account") }}</div>
              <div class="ui-detail-list">
                <div>
                  <div class="meta-label">{{ t("settings.currentIdentity") }}</div>
                  <div class="ui-value-strong">NomoClaw Operator</div>
                </div>
                <div>
                  <div class="meta-label">{{ t("settings.defaultTimezone") }}</div>
                  <div class="ui-value-strong">Asia/Shanghai</div>
                </div>
              </div>
            </section>

            <section class="surface-card">
              <div class="surface-card-title">{{ t("settings.preferences") }}</div>
              <div class="ui-detail-list">
                <div>
                  <div class="meta-label">{{ t("settings.language") }}</div>
                  <div class="ui-value-strong settings-language-select-wrap">
                    <n-select
                      class="settings-language-select"
                      :value="uiPreferencesStore.locale"
                      :options="localeOptions"
                      :clearable="false"
                      @update:value="onLocaleChange"
                    />
                  </div>
                </div>
                <div>
                  <div class="meta-label">{{ t("settings.runtimeLogVisibility") }}</div>
                  <div class="settings-switch-row">
                    <n-switch
                      :value="uiPreferencesStore.chatRuntimeLogVisible"
                      @update:value="(value) => uiPreferencesStore.setChatRuntimeLogVisible(Boolean(value))"
                    />
                    <span class="settings-switch-state">{{ uiPreferencesStore.chatRuntimeLogVisible ? t("settings.runtimeLogOn") : t("settings.runtimeLogOff") }}</span>
                  </div>
                </div>
              </div>
            </section>

            <section class="surface-card settings-diagnostics-card">
              <div class="settings-diagnostics-title-row">
                <div>
                  <div class="surface-card-title">{{ t("settings.diagnostics") }}</div>
                  <div class="settings-diagnostics-subtitle">{{ t("settings.diagnosticsSubtitle") }}</div>
                </div>
              </div>
              <div class="settings-diagnostics-health">
                <div>
                  <div class="meta-label">{{ t("settings.diagnosticsHealth") }}</div>
                  <div class="settings-diagnostics-health-value" :class="{ 'is-warning': diagnosticsHasErrors }">
                    {{ diagnosticsHasErrors ? t("settings.diagnosticsNeedsAttention") : t("settings.diagnosticsHealthy") }}
                  </div>
                </div>
                <div>
                  <div class="meta-label">{{ t("settings.diagnosticsLast24Hours") }}</div>
                  <div class="ui-value-strong">{{ diagnosticsSummary.recent24hCount }}</div>
                </div>
              </div>
              <div v-if="diagnosticsSummary.latestOccurredTime" class="settings-diagnostics-latest">
                <span>{{ diagnosticsSummary.latestOccurredTime }}</span>
              </div>
              <div class="settings-diagnostics-actions">
                <n-button tertiary size="small" :loading="diagnosticsLoading" @click="loadDiagnosticsSummary">
                  <template #icon>
                    <n-icon :component="RefreshCw" />
                  </template>
                  {{ t("settings.diagnosticsRefresh") }}
                </n-button>
                <n-button type="primary" size="small" @click="openDiagnosticsPage">
                  {{ t("settings.diagnosticsViewLogs") }}
                </n-button>
              </div>
            </section>
            <section class="surface-card settings-token-usage-card">
              <div>
                <div class="surface-card-title">Token 用量</div>
                <div class="settings-diagnostics-subtitle">查看聊天和锦囊总结的模型 token 消耗及调用明细。</div>
              </div>
              <div class="settings-token-usage-actions">
                <n-button tertiary @click="openTokenUsagePage">查看 Token 用量</n-button>
              </div>
            </section>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.settings-grid {
  grid-template-columns: repeat(auto-fit, minmax(min(100%, var(--size-300)), 1fr));
  align-items: start;
}

.settings-language-select-wrap {
  margin-top: var(--space-1_5);
  display: inline-block;
}

.settings-language-select {
  width: var(--size-180);
  min-width: var(--size-170);
}

.settings-switch-row {
  margin-top: var(--space-1_5);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--space-8);
}

.settings-switch-state {
  font-size: var(--text-body-size);
  font-weight: 400;
  line-height: 1.2;
}

.settings-updater-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.settings-updater-version,
.settings-updater-progress,
.settings-updater-error {
  font-size: var(--text-caption-size);
  color: var(--color-text-secondary);
}

.settings-updater-error {
  color: var(--color-danger);
}

.settings-diagnostics-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.settings-token-usage-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.settings-token-usage-actions {
  display: flex;
  justify-content: flex-start;
}

.settings-diagnostics-title-row,
.settings-diagnostics-health,
.settings-diagnostics-latest,
.settings-diagnostics-actions {
  display: flex;
  align-items: center;
}

.settings-diagnostics-title-row,
.settings-diagnostics-health {
  justify-content: space-between;
  gap: var(--space-3);
}

.settings-diagnostics-subtitle {
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
  line-height: 1.4;
}

.settings-diagnostics-health {
  padding: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}

.settings-diagnostics-health-value {
  margin-top: var(--space-1);
  color: var(--color-success);
  font-size: var(--text-body-size);
  font-weight: 650;
}

.settings-diagnostics-health-value.is-warning {
  color: var(--color-warning);
}

.settings-diagnostics-latest {
  gap: var(--space-2);
  min-width: 0;
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
}

.settings-diagnostics-latest span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-diagnostics-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
