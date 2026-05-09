<script setup lang="ts">
import { computed, h, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NDataTable, NDatePicker, NEmpty, NPagination, NSelect, NTag, type DataTableColumns } from "naive-ui";
import { useRouter } from "vue-router";
import { ChevronLeft } from "lucide-vue-next";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { cronApi } from "@/api/cronApi";
import { message } from "@/discrete";
import type { CronJobExecutionResult } from "@/types/api";
import { formatDateTime } from "@/utils/format";

const { t } = useI18n();
const router = useRouter();

const loading = ref(false);
const page = ref(1);
const pageSize = 20;
const total = ref(0);
const items = ref<CronJobExecutionResult[]>([]);
const agentUid = ref<string>("");
const status = ref<string>("");
const dateRange = ref<[number, number] | null>(null);
const dateQuick = ref<"7d" | "30d" | "custom">("7d");
const agentOptions = ref<Array<{ label: string; value: string }>>([]);
const dateQuickOptions = computed(() => [
  { label: t("cron.history.quick7d"), value: "7d" },
  { label: t("cron.history.quick30d"), value: "30d" },
  { label: t("cron.history.quickCustom"), value: "custom" }
]);

const statusOptions = computed(() => [
  { label: t("cron.history.statusAll"), value: "" },
  { label: t("cron.history.statusCompleted"), value: "COMPLETED" },
  { label: t("cron.history.statusFailed"), value: "FAILED" }
]);

function toDateText(timestamp?: number) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function quickDateRange(type: "7d" | "30d") {
  const now = new Date();
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999).getTime();
  const days = type === "7d" ? 7 : 30;
  const startDate = new Date(now);
  startDate.setDate(now.getDate() - (days - 1));
  const start = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate(), 0, 0, 0, 0).getTime();
  return [start, end] as [number, number];
}

function statusTagType(value?: string | null) {
  const normalized = String(value || "").toUpperCase();
  if (normalized === "FAILED" || normalized === "CANCELED") return "error";
  if (normalized === "RUNNING") return "warning";
  if (normalized === "WAITING_APPROVAL") return "warning";
  return "success";
}

function statusLabel(value?: string | null) {
  const normalized = String(value || "").toUpperCase();
  if (normalized === "COMPLETED") return t("common.success");
  if (normalized === "FAILED") return t("common.failed");
  if (normalized === "CANCELED") return t("cron.history.statusCanceled");
  if (normalized === "RUNNING") return t("cron.execution.runningTag");
  if (normalized === "WAITING_APPROVAL") return t("cron.history.statusWaitingApproval");
  return normalized || t("common.unknown");
}

function openExecution(item: CronJobExecutionResult) {
  const executionUid = String(item.executionUid || "").trim();
  const conversationUid = String(item.conversationUid || "").trim();
  const messageUid = String(item.messageUid || "").trim();
  const nextAgentUid = String(item.agentUid || "").trim();
  const jobUid = String(item.jobUid || "").trim();
  if (!executionUid) {
    return;
  }
  void cronApi.markExecutionRead(executionUid);
  if (!conversationUid) {
    message.warning("执行会话准备中，请稍后重试。");
    return;
  }
  void router.push({
    path: "/",
    query: {
      source: "cron",
      executionUid,
      conversationUid,
      ...(messageUid ? { messageUid } : {}),
      ...(nextAgentUid ? { agentUid: nextAgentUid } : {}),
      ...(jobUid ? { jobUid } : {})
    }
  });
}

const columns: DataTableColumns<CronJobExecutionResult> = [
  {
    title: () => t("cron.history.colTask"),
    key: "jobTitle",
    render: (row) => row.jobTitle || t("format.fallbackNoName")
  },
  {
    title: () => t("cron.history.colAgent"),
    key: "agentDisplayName",
    render: (row) => row.agentDisplayName || t("format.fallbackNoAgent")
  },
  {
    title: () => t("cron.history.colStatus"),
    key: "status",
    render: (row) => h(NTag, { size: "small", type: statusTagType(row.status) }, { default: () => statusLabel(row.status) })
  },
  {
    title: () => t("cron.history.colTime"),
    key: "executedTime",
    render: (row) => formatDateTime(row.executedTime)
  },
  {
    title: () => t("cron.history.colAction"),
    key: "actions",
    width: 132,
    render: (row) => h(
      NButton,
      { text: true, type: "primary", disabled: !row.executionUid, onClick: () => openExecution(row) },
      { default: () => t("cron.execution.viewLog") }
    )
  }
];

async function loadAgents() {
  const groups = await cronApi.listAgentGroups();
  const map = new Map<string, { label: string; sortIndex: number }>();
  groups.forEach((group) => {
    (group.agents || []).forEach((agent) => {
      const key = String(agent.agentUid || "").trim();
      if (!key) return;
      const nextLabel = String(agent.displayName || agent.agentName || key);
      const nextSortIndex = Number.isFinite(agent.sortIndex) ? Number(agent.sortIndex) : Number.MAX_SAFE_INTEGER;
      const previous = map.get(key);
      if (!previous) {
        map.set(key, { label: nextLabel, sortIndex: nextSortIndex });
        return;
      }
      if (nextSortIndex < previous.sortIndex) {
        map.set(key, { label: nextLabel, sortIndex: nextSortIndex });
      }
    });
  });
  agentOptions.value = [
    { label: t("cron.history.agentAll"), value: "" },
    ...Array.from(map.entries())
      .sort((a, b) => {
        const sortDiff = a[1].sortIndex - b[1].sortIndex;
        if (sortDiff !== 0) return sortDiff;
        return a[1].label.localeCompare(b[1].label);
      })
      .map(([value, item]) => ({ label: item.label, value }))
  ];
}

async function loadHistory() {
  loading.value = true;
  try {
    const effectiveRange = dateQuick.value === "custom"
      ? dateRange.value
      : quickDateRange(dateQuick.value);
    const startDate = toDateText(effectiveRange?.[0]);
    const endDate = toDateText(effectiveRange?.[1]);
    const response = await cronApi.listGlobalExecutionHistory({
      agentUid: agentUid.value || undefined,
      status: status.value || undefined,
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      page: page.value,
      pageSize
    });
    items.value = response.items;
    total.value = response.total;
  } finally {
    loading.value = false;
  }
}

async function refresh() {
  await Promise.all([loadAgents(), loadHistory()]);
}

async function applyFilters() {
  page.value = 1;
  await loadHistory();
}

function goBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  void router.push("/cron");
}

onMounted(() => {
  void refresh();
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content cron-history-page">
          <div class="history-back-row">
            <button type="button" class="history-back-btn" @click="goBack">
              <ChevronLeft :size="14" />
              <span>返回</span>
            </button>
          </div>
          <AppPageHeader :title="t('cron.history.title')" :subtitle="t('cron.history.subtitle')" />

          <section class="panel">
            <div class="panel-body history-filters">
              <n-select
                v-model:value="agentUid"
                :options="agentOptions"
                :placeholder="t('cron.history.agentPlaceholder')"
                size="small"
                class="history-filter-item history-filter-agent"
              />
              <n-select
                v-model:value="status"
                :options="statusOptions"
                :placeholder="t('cron.history.statusPlaceholder')"
                size="small"
                class="history-filter-item history-filter-status"
              />
              <n-select
                v-model:value="dateQuick"
                :options="dateQuickOptions"
                size="small"
                class="history-filter-item history-filter-quick"
              />
              <n-date-picker
                v-if="dateQuick === 'custom'"
                v-model:value="dateRange"
                type="daterange"
                clearable
                :start-placeholder="t('cron.history.startDate')"
                :end-placeholder="t('cron.history.endDate')"
                size="small"
                class="history-filter-item history-filter-date"
              />
              <n-button type="primary" size="small" :loading="loading" class="history-filter-item history-filter-action" @click="applyFilters">
                {{ t("cron.history.query") }}
              </n-button>
            </div>
          </section>

          <section class="panel history-table-panel">
            <div class="panel-body history-table-body">
              <n-empty v-if="!loading && !items.length" :description="t('cron.history.empty')" class="history-empty-state" />
              <n-data-table
                v-else
                :loading="loading"
                :columns="columns"
                :data="items"
                :bordered="false"
                size="small"
                class="history-table"
              />
              <div class="history-pagination">
                <n-pagination
                  v-model:page="page"
                  :item-count="total"
                  :page-size="pageSize"
                  :page-slot="7"
                  @update:page="loadHistory"
                />
              </div>
            </div>
          </section>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.cron-history-page {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: var(--space-4);
  min-height: 0;
}

.history-back-row {
  display: flex;
  align-items: center;
}

.history-back-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
  cursor: pointer;
}

.history-back-btn:hover {
  color: var(--color-brand-400);
}

.history-filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: center;
  gap: var(--space-3);
}

.history-filter-item {
  min-width: 0;
}

.history-filter-agent,
.history-filter-status {
  width: 100%;
}

.history-filter-date {
  width: 100%;
}

.history-filter-quick {
  width: 100%;
}

.history-filter-action {
  width: 100%;
  min-width: 96px;
}

.history-filter-date :deep(.n-date-picker-daterange .n-date-picker-input) {
  flex: 1 1 0;
  min-width: 0;
}

.history-table-panel {
  min-height: 0;
}

.history-table-body {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: var(--space-3);
  min-height: 0;
}

.history-table {
  border: var(--size-1) solid var(--color-border-panel);
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.history-pagination {
  display: flex;
  justify-content: flex-end;
}

.history-empty-state {
  margin-top: calc(var(--space-3) * 3);
}

@media (max-width: 1080px) {
  .history-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .history-filter-action {
    justify-self: end;
    width: auto;
  }

  .history-filter-date {
    grid-column: span 2;
  }
}

@media (max-width: 720px) {
  .history-filters {
    grid-template-columns: 1fr;
  }

  .history-filter-date {
    grid-column: span 1;
  }

  .history-filter-action {
    justify-self: stretch;
    width: 100%;
  }
}

@media (max-width: 768px) {
  .history-filters {
    display: grid;
    grid-template-columns: 1fr;
  }

  .history-filter-agent,
  .history-filter-status,
  .history-filter-quick,
  .history-filter-date,
  .history-filter-action {
    width: 100%;
  }
}
</style>
