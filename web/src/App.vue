<script setup lang="ts">
import { computed } from "vue";
import { NConfigProvider, NGlobalStyle, darkTheme, zhCN, enUS, dateZhCN, dateEnUS } from "naive-ui";
import { useI18n } from "vue-i18n";
import { RouterView, useRoute, useRouter } from "vue-router";
import { resolveThemeOverrides } from "@/theme";
import { useModelGateStore } from "@/stores/modelGate";
import { useUiPreferencesStore } from "@/stores/uiPreferences";

const uiPreferencesStore = useUiPreferencesStore();
const modelGateStore = useModelGateStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();

uiPreferencesStore.init();
void modelGateStore.refreshModelReadiness();

const appThemeOverrides = computed(() => resolveThemeOverrides(uiPreferencesStore.themeMode));
const naiveTheme = computed(() => (uiPreferencesStore.themeMode === "dark" ? darkTheme : undefined));
const naiveLocale = computed(() => (uiPreferencesStore.locale === "zh-CN" ? zhCN : enUS));
const naiveDateLocale = computed(() => (uiPreferencesStore.locale === "zh-CN" ? dateZhCN : dateEnUS));
const shouldShowModelGate = computed(() => modelGateStore.shouldShowPrompt && route.path !== "/models");
const modelGateStatus = computed(() =>
  modelGateStore.checkError ? t("modelGate.status.checkFailed") : t("modelGate.status.missingModel")
);

function openModelsPage() {
  void router.push("/models");
}

function dismissModelGate() {
  modelGateStore.dismissPrompt();
}
</script>

<template>
  <n-config-provider
    :theme="naiveTheme"
    :theme-overrides="appThemeOverrides"
    :locale="naiveLocale"
    :date-locale="naiveDateLocale"
  >
    <n-global-style />
    <div class="app-root-shell">
      <RouterView />
      <div
        v-if="shouldShowModelGate"
        class="model-gate-overlay"
        role="dialog"
        aria-modal="true"
        :aria-label="t('modelGate.title')"
      >
        <div class="model-gate-panel">
          <div class="model-gate-title">{{ t("modelGate.title") }}</div>
          <div class="model-gate-description">{{ t("modelGate.description") }}</div>
          <div class="model-gate-status">{{ modelGateStatus }}</div>
          <div class="model-gate-actions">
            <button class="model-gate-btn model-gate-btn-primary" type="button" @click="openModelsPage">
              {{ t("modelGate.actions.goConfig") }}
            </button>
            <button class="model-gate-btn model-gate-btn-secondary" type="button" @click="dismissModelGate">
              {{ t("modelGate.actions.dismissForever") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </n-config-provider>
</template>

<style scoped>
.app-root-shell {
  position: relative;
  width: 100%;
  height: 100%;
}

.model-gate-overlay {
  position: fixed;
  z-index: 999;
  inset: 0 0 0 var(--size-80);
  display: grid;
  place-items: center;
  padding: var(--space-6);
  background: var(--color-bg-overlay-mask);
  backdrop-filter: blur(var(--size-2));
}

.model-gate-panel {
  width: min(var(--container-sm), calc(100vw - var(--size-80) - var(--size-48)));
  padding: var(--space-6);
  border-radius: var(--radius-xl);
  border: var(--size-1) solid var(--color-border-overlay-strong);
  background: var(--color-bg-overlay-panel);
  box-shadow: var(--color-shadow-overlay);
}

.model-gate-title {
  font-size: var(--text-title-md-size);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
}

.model-gate-description {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.model-gate-status {
  margin-top: var(--space-3);
  color: var(--color-warning-500);
  font-size: var(--text-body-size);
}

.model-gate-actions {
  margin-top: var(--space-5);
  display: flex;
  gap: var(--space-3);
}

.model-gate-btn {
  border: 0;
  border-radius: var(--radius-pill);
  padding: var(--space-2_5) var(--space-4_5);
  font-size: var(--text-body-size);
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
}

.model-gate-btn-primary {
  background: var(--color-button-primary-bg);
  color: var(--color-button-primary-text);
}

.model-gate-btn-primary:hover {
  background: var(--color-button-primary-bg-hover);
}

.model-gate-btn-secondary {
  background: var(--color-bg-overlay-control);
  color: var(--color-text-secondary);
}

.model-gate-btn-secondary:hover {
  background: var(--color-bg-overlay-control-hover);
}

@media (max-width: 1120px) {
  .model-gate-overlay {
    inset: 0;
  }

  .model-gate-panel {
    width: min(var(--container-sm), calc(100vw - var(--size-32)));
  }
}
</style>
