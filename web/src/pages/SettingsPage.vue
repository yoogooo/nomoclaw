<script setup lang="ts">
import { NButton, NSelect, NSwitch } from "naive-ui";
import { useI18n } from "vue-i18n";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { useUiPreferencesStore } from "@/stores/uiPreferences";
import { useUpdateManagerStore } from "@/stores/updateManager";
import type { AppLocale } from "@/i18n";
import { computed, onMounted } from "vue";

const uiPreferencesStore = useUiPreferencesStore();
const updateManagerStore = useUpdateManagerStore();
uiPreferencesStore.init();
const { t } = useI18n();
const localeOptions = computed(() => [
  { label: t("settings.languageZhCN"), value: "zh-CN" },
  { label: t("settings.languageEnUS"), value: "en-US" }
]);
const downloadPercentText = computed(() => {
  const value = updateManagerStore.downloadProgress;
  if (value === null || value === undefined) return "";
  return `${Math.round(Math.min(Math.max(value, 0), 1) * 100)}%`;
});

onMounted(() => {
  void updateManagerStore.init();
});

function onLocaleChange(value: AppLocale | null) {
  if (!value) return;
  uiPreferencesStore.setLocale(value);
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
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.settings-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

@media (max-width: var(--size-breakpoint-lg)) {
  .settings-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: var(--size-breakpoint-md)) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
