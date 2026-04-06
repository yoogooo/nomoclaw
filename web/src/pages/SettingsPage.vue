<script setup lang="ts">
import { Moon, Sun } from "lucide-vue-next";
import { NSelect } from "naive-ui";
import { useI18n } from "vue-i18n";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { useUiPreferencesStore } from "@/stores/uiPreferences";
import type { AppLocale } from "@/i18n";
import { computed } from "vue";

const uiPreferencesStore = useUiPreferencesStore();
uiPreferencesStore.init();
const { t } = useI18n();
const localeOptions = computed(() => [
  { label: t("settings.languageZhCN"), value: "zh-CN" },
  { label: t("settings.languageEnUS"), value: "en-US" }
]);

function onThemeModeChange(value: "light" | "dark") {
  uiPreferencesStore.setThemeMode(value);
}

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
          />
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
                <div class="meta-label">{{ t("settings.layoutMode") }}</div>
                <div class="ui-value-strong">{{ t("settings.workspaceMode") }}</div>
              </div>
              <div>
                <div class="meta-label">{{ t("settings.theme") }}</div>
                <div class="ui-value-strong settings-theme-toggle">
                  <button
                    class="theme-option"
                    :class="{ active: uiPreferencesStore.themeMode === 'light' }"
                    type="button"
                    @click="onThemeModeChange('light')"
                  >
                    <Sun :size="16" />
                    <span>{{ t("settings.themeLight") }}</span>
                  </button>
                  <button
                    class="theme-option"
                    :class="{ active: uiPreferencesStore.themeMode === 'dark' }"
                    type="button"
                    @click="onThemeModeChange('dark')"
                  >
                    <Moon :size="16" />
                    <span>{{ t("settings.themeDark") }}</span>
                  </button>
                </div>
              </div>
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

.settings-theme-toggle {
  margin-top: var(--space-1_5);
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  width: fit-content;
  padding: var(--space-1);
  border: var(--size-1) solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  background: var(--color-bg-surface-soft);
}

.settings-language-select-wrap {
  margin-top: var(--space-1_5);
  display: inline-block;
}

.settings-language-select {
  width: var(--size-180);
  min-width: var(--size-170);
}

.theme-option {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1_5);
  min-width: var(--size-92);
  justify-content: center;
  padding: var(--space-1_5) var(--space-3);
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--font-size-lg);
  font-weight: 400;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.theme-option:hover {
  color: var(--color-text-primary);
}

.theme-option.active {
  background: var(--color-accent-brand);
  color: var(--color-button-primary-text);
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
