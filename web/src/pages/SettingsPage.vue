<script setup lang="ts">
import { Moon, Sun } from "lucide-vue-next";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { useUiPreferencesStore } from "@/stores/uiPreferences";

const uiPreferencesStore = useUiPreferencesStore();
uiPreferencesStore.init();

function onThemeModeChange(value: "light" | "dark") {
  uiPreferencesStore.setThemeMode(value);
}
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content">
          <AppPageHeader
            title="设置"
            subtitle="管理当前工作区的账户信息和界面偏好。"
          />
          <div class="page-section-grid settings-grid">
          <section class="surface-card">
            <div class="surface-card-title">账户信息</div>
            <div class="ui-detail-list">
              <div>
                <div class="meta-label">当前身份</div>
                <div class="ui-value-strong">NomoClaw Operator</div>
              </div>
              <div>
                <div class="meta-label">默认时区</div>
                <div class="ui-value-strong">Asia/Shanghai</div>
              </div>
            </div>
          </section>

          <section class="surface-card">
            <div class="surface-card-title">界面偏好</div>
            <div class="ui-detail-list">
              <div>
                <div class="meta-label">布局模式</div>
                <div class="ui-value-strong">聊天工作台</div>
              </div>
              <div>
                <div class="meta-label">主题风格</div>
                <div class="ui-value-strong settings-theme-toggle">
                  <button
                    class="theme-option"
                    :class="{ active: uiPreferencesStore.themeMode === 'light' }"
                    type="button"
                    @click="onThemeModeChange('light')"
                  >
                    <Sun :size="16" />
                    <span>Light</span>
                  </button>
                  <button
                    class="theme-option"
                    :class="{ active: uiPreferencesStore.themeMode === 'dark' }"
                    type="button"
                    @click="onThemeModeChange('dark')"
                  >
                    <Moon :size="16" />
                    <span>Dark</span>
                  </button>
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
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.theme-option:hover {
  color: var(--color-text-primary);
}

.theme-option.active {
  background: var(--color-accent-brand);
  color: var(--color-text-inverse);
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
