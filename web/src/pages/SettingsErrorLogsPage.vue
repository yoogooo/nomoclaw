<script setup lang="ts">
import { ChevronLeft, Copy, RefreshCw } from "lucide-vue-next";
import { NButton, NDataTable, NIcon, NModal } from "naive-ui";
import type { DataTableColumns } from "naive-ui";
import { computed, h, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import { systemDiagnosticsApi } from "@/api/systemDiagnosticsApi";
import { message as discreteMessage } from "@/discrete";
import type { SystemErrorLog } from "@/types/api";

const { t } = useI18n();
const router = useRouter();
const errorLogs = ref<SystemErrorLog[]>([]);
const logsLoading = ref(false);
const selectedEvent = ref<SystemErrorLog | null>(null);
const detailModalVisible = computed({
  get: () => selectedEvent.value !== null,
  set: (value: boolean) => {
    if (!value) {
      selectedEvent.value = null;
    }
  }
});
const logColumns = computed<DataTableColumns<SystemErrorLog>>(() => [
  {
    title: t("settings.diagnosticsOccurredAt"),
    key: "occurredTime",
    width: 180,
    render(row) {
      return h("span", { class: "settings-error-log-time" }, formatOccurredTime(row.occurredTime));
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

onMounted(() => {
  void loadErrorLogs();
});

function logSummary(event: SystemErrorLog) {
  return [event.message, event.detail].filter(Boolean).join(" ");
}

function truncateLog(text: string) {
  const trimmed = text.trim();
  return trimmed.length > 200 ? `${trimmed.slice(0, 200)}...` : trimmed;
}

function formatOccurredTime(value: string) {
  return value
    .replace(/^(.+[T ]\d{2}:\d{2}:\d{2})(?:\.\d+)?(.*)$/, "$1$2")
    .replace(/^(\d{4}-\d{2}-\d{2})T/, "$1 ");
}

function openLogDetail(event: SystemErrorLog) {
  selectedEvent.value = event;
}

async function copySelectedLog() {
  if (!selectedEvent.value) return;
  try {
    await window.navigator.clipboard.writeText(logSummary(selectedEvent.value));
    discreteMessage.success(t("settings.diagnosticsCopyLogSuccess"));
  } catch {
    discreteMessage.error(t("chat.messages.copyFailed"));
  }
}

function rowProps(row: SystemErrorLog) {
  return {
    class: "settings-error-log-table-row",
    onClick: () => openLogDetail(row)
  };
}

async function loadErrorLogs() {
  logsLoading.value = true;
  try {
    errorLogs.value = await systemDiagnosticsApi.listErrorLogs(100);
  } catch {
    errorLogs.value = [];
  } finally {
    logsLoading.value = false;
  }
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
          </header>

          <section class="surface-card settings-error-logs-panel">
            <div class="settings-error-logs-toolbar">
              <div>
                <div class="surface-card-title">{{ t("settings.diagnosticsDrawerSubtitle") }}</div>
                <div class="settings-error-logs-meta">
                  {{ t("settings.diagnosticsDrawerMeta", { count: errorLogs.length }) }}
                </div>
              </div>
              <div class="settings-error-logs-actions">
                <n-button tertiary :loading="logsLoading" @click="loadErrorLogs">
                  <template #icon>
                    <n-icon :component="RefreshCw" />
                  </template>
                  {{ t("settings.diagnosticsRefresh") }}
                </n-button>
              </div>
            </div>

            <n-data-table
              :columns="logColumns"
              :data="errorLogs"
              :loading="logsLoading"
              :row-key="(row) => row.logUid"
              :row-props="rowProps"
              size="small"
            >
              <template #empty>
                {{ t("settings.diagnosticsEmpty") }}
              </template>
            </n-data-table>
          </section>
        </div>
      </main>
    </div>

    <n-modal
      v-model:show="detailModalVisible"
      preset="card"
      class="settings-error-log-modal"
      style="width: 70vw; max-width: calc(100vw - var(--space-8))"
      :title="selectedEvent?.title || t('settings.diagnosticsDrawerTitle')"
      :bordered="false"
    >
      <div v-if="selectedEvent" class="settings-error-log-modal-body">
        <div class="settings-error-log-modal-actions">
          <n-button tertiary size="small" @click="copySelectedLog">
            <template #icon>
              <n-icon :component="Copy" />
            </template>
            {{ t("settings.diagnosticsCopyLog") }}
          </n-button>
        </div>
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
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-height: 0;
}

.settings-error-log-modal-actions {
  display: flex;
  justify-content: flex-end;
}

.settings-error-log-modal-content {
  overflow: auto;
  min-height: var(--size-220);
  max-height: min(62vh, var(--size-420));
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
