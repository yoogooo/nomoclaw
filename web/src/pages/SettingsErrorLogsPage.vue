<script setup lang="ts">
import { ChevronLeft, RefreshCw } from "lucide-vue-next";
import { NButton, NDataTable, NIcon, NModal } from "naive-ui";
import type { DataTableColumns } from "naive-ui";
import { computed, h, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import type { DiagnosticEvent } from "@/composables/useSystemDiagnosticsMock";
import { useSystemDiagnosticsMock } from "@/composables/useSystemDiagnosticsMock";

const { t } = useI18n();
const router = useRouter();
const { diagnosticEvents } = useSystemDiagnosticsMock();
const selectedEvent = ref<DiagnosticEvent | null>(null);
const detailModalVisible = computed({
  get: () => selectedEvent.value !== null,
  set: (value: boolean) => {
    if (!value) {
      selectedEvent.value = null;
    }
  }
});
const logColumns = computed<DataTableColumns<DiagnosticEvent>>(() => [
  {
    title: t("settings.diagnosticsOccurredAt"),
    key: "occurredAt",
    width: 180,
    render(row) {
      return h("span", { class: "settings-error-log-time" }, row.occurredAt);
    }
  },
  {
    title: t("settings.diagnosticsLogContent"),
    key: "summary",
    ellipsis: {
      tooltip: false
    },
    render(row) {
      return h("span", { class: "settings-error-log-summary" }, truncateLog(logSummary(row)));
    }
  }
]);

function logSummary(event: DiagnosticEvent) {
  return [event.message, event.detail].filter(Boolean).join(" ");
}

function truncateLog(text: string) {
  const trimmed = text.trim();
  return trimmed.length > 200 ? `${trimmed.slice(0, 200)}...` : trimmed;
}

function openLogDetail(event: DiagnosticEvent) {
  selectedEvent.value = event;
}

function rowProps(row: DiagnosticEvent) {
  return {
    class: "settings-error-log-table-row",
    onClick: () => openLogDetail(row)
  };
}

function backToSettings() {
  void router.push({ name: "settings" });
}
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content settings-error-logs-page">
          <header class="settings-error-logs-header">
            <button class="settings-error-logs-back" type="button" @click="backToSettings">
              <n-icon :component="ChevronLeft" />
              <span>{{ t("settings.title") }}</span>
            </button>
            <div class="settings-error-logs-heading">
              <div class="settings-error-logs-title">{{ t("settings.diagnosticsDrawerTitle") }}</div>
            </div>
          </header>

          <section class="surface-card settings-error-logs-panel">
            <div class="settings-error-logs-toolbar">
              <div>
                <div class="surface-card-title">{{ t("settings.diagnosticsDrawerSubtitle") }}</div>
                <div class="settings-error-logs-meta">
                  {{ t("settings.diagnosticsDrawerMeta", { count: diagnosticEvents.length }) }}
                </div>
              </div>
              <div class="settings-error-logs-actions">
                <n-button tertiary>
                  <template #icon>
                    <n-icon :component="RefreshCw" />
                  </template>
                  {{ t("settings.diagnosticsRefresh") }}
                </n-button>
              </div>
            </div>

            <n-data-table
              :columns="logColumns"
              :data="diagnosticEvents"
              :row-key="(row) => row.id"
              :row-props="rowProps"
              size="small"
            />
          </section>
        </div>
      </main>
    </div>

    <n-modal
      v-model:show="detailModalVisible"
      preset="card"
      class="settings-error-log-modal"
      style="width: min(var(--container-xs), calc(100vw - var(--space-8)))"
      :title="selectedEvent?.title || t('settings.diagnosticsDrawerTitle')"
      :bordered="false"
    >
      <div v-if="selectedEvent" class="settings-error-log-modal-body">
        <pre class="settings-error-log-modal-content">{{ logSummary(selectedEvent) }}</pre>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
.settings-error-logs-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.settings-error-logs-header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.settings-error-logs-heading {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-1);
}

.settings-error-logs-back {
  display: flex;
  align-items: center;
  gap: var(--space-1_5);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: var(--text-body-size);
  font-weight: 600;
  line-height: 1.4;
}

.settings-error-logs-back:hover {
  color: var(--color-text-primary);
}

.settings-error-logs-title {
  color: var(--color-text-primary);
  font-size: var(--text-title-md-size);
  font-weight: 750;
  line-height: 1.2;
}

.settings-error-logs-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.settings-error-logs-toolbar,
.settings-error-logs-actions {
  display: flex;
  align-items: center;
}

.settings-error-logs-toolbar {
  justify-content: space-between;
  gap: var(--space-3);
}

.settings-error-logs-meta {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
  line-height: 1.4;
}

.settings-error-logs-actions {
  justify-content: flex-end;
  gap: var(--space-2);
}

.settings-error-log-time {
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
  line-height: 1.5;
  white-space: nowrap;
}

.settings-error-log-summary {
  min-width: 0;
  color: var(--color-text-primary);
  font-size: var(--text-body-size);
  line-height: 1.5;
}

:deep(.settings-error-log-table-row) {
  cursor: pointer;
}

.settings-error-log-modal-body {
  min-height: 0;
}

.settings-error-log-modal-content {
  overflow: auto;
  max-height: min(58vh, var(--size-420));
  margin: 0;
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
  font-size: var(--text-code-size);
  line-height: 1.5;
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .settings-error-logs-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
