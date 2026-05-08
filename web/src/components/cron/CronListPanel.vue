<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NDropdown, NEmpty, NFlex, NModal, NTag, NTooltip, type DropdownOption } from "naive-ui";
import { Ellipsis, Pencil, Play } from "lucide-vue-next";
import { useCronJobsStore } from "@/stores/cronJobs";
import type { CronJob } from "@/types/api";
import { getSortLocale } from "@/i18n";
import { cronStatusLabel, displayCronJobTitle, fallbackAgentLabel, formatDateTime, humanizeCronExpression } from "@/utils/format";

const emit = defineEmits<{
  (event: "create"): void;
  (event: "edit", job: CronJob): void;
}>();

const cronJobsStore = useCronJobsStore();
const { t } = useI18n();
const hoveredJobUid = ref<string | null>(null);
const menuOpenJobUid = ref<string | null>(null);
const showResultModal = ref(false);
const resultModalTitle = ref("");
const resultModalRows = ref<Array<{ executedTime: string; status: string; summary: string }>>([]);

const jobsSorted = computed(() => {
  const order = new Map<string, number>();
  let index = 0;
  cronJobsStore.agentGroups.forEach((group) => {
    group.agents.forEach((agent) => {
      if (!order.has(agent.agentUid)) {
        order.set(agent.agentUid, index++);
      }
    });
  });

  return [...cronJobsStore.jobs].sort((left, right) => {
    const leftSortIndex = order.get(left.agentUid) ?? Number.MAX_SAFE_INTEGER;
    const rightSortIndex = order.get(right.agentUid) ?? Number.MAX_SAFE_INTEGER;
    if (leftSortIndex !== rightSortIndex) {
      return leftSortIndex - rightSortIndex;
    }
    const labelCompare = fallbackAgentLabel(left).localeCompare(fallbackAgentLabel(right), getSortLocale());
    if (labelCompare !== 0) {
      return labelCompare;
    }
    return displayCronJobTitle(left).localeCompare(displayCronJobTitle(right), getSortLocale());
  });
});

async function toggleJobStatus(job: CronJob) {
  if (job.status === "ACTIVE") {
    await cronJobsStore.pauseJob(job.jobUid);
    return;
  }
  if (job.status === "PAUSED") {
    await cronJobsStore.resumeJob(job.jobUid);
  }
}

async function openRecentResults(job: CronJob) {
  await cronJobsStore.selectJob(job.jobUid);
  resultModalTitle.value = `${displayCronJobTitle(job)} · 最近执行记录`;
  resultModalRows.value = cronJobsStore.currentResults.slice(0, 10).map((item) => ({
    executedTime: item.executedTime,
    status: item.status,
    summary: item.summary
  }));
  showResultModal.value = true;
}

function moreOptions(job: CronJob): DropdownOption[] {
  return [
    { label: "删除", key: "delete" },
    { label: job.status === "ACTIVE" ? "暂停" : "开启", key: "toggle" },
    { label: "执行记录", key: "results" }
  ];
}

async function onMoreSelect(job: CronJob, key: string | number) {
  if (key === "delete") {
    cronJobsStore.confirmDeleteJob(job);
    return;
  }
  if (key === "toggle") {
    await toggleJobStatus(job);
    return;
  }
  if (key === "results") {
    await openRecentResults(job);
  }
}

function onMoreVisible(jobUid: string, show: boolean) {
  menuOpenJobUid.value = show ? jobUid : (menuOpenJobUid.value === jobUid ? null : menuOpenJobUid.value);
}

function onJobMouseLeave(jobUid: string) {
  if (menuOpenJobUid.value !== jobUid) {
    hoveredJobUid.value = null;
  }
}
</script>

<template>
  <div class="panel">
    <div class="panel-header cron-list-header">
      <div class="cron-list-header-top">
        <div>
          <div class="panel-title ui-title-xl">{{ t("cron.list.title") }}</div>
        </div>
        <n-button type="primary" @click="emit('create')">{{ t("cron.list.createTask") }}</n-button>
      </div>
    </div>
    <div class="panel-body cron-list-panel">
      <div class="scroll-area cron-scroll-body">
        <div v-if="cronJobsStore.jobs.length" class="cron-table-wrap">
          <table class="cron-table">
            <colgroup>
              <col class="cron-col-title">
              <col class="cron-col-agent">
              <col class="cron-col-status">
              <col class="cron-col-cycle">
            </colgroup>
            <thead>
              <tr class="cron-table-head-row">
                <th class="cron-table-head-cell">{{ t("cron.list.taskName") }}</th>
                <th class="cron-table-head-cell">{{ t("cron.list.agentName") }}</th>
                <th class="cron-table-head-cell">{{ t("cron.list.status") }}</th>
                <th class="cron-table-head-cell">{{ t("cron.list.schedule") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="job in jobsSorted"
                :key="job.jobUid"
                class="cron-job-row"
                @mouseenter="hoveredJobUid = job.jobUid"
                @mouseleave="onJobMouseLeave(job.jobUid)"
              >
                <td>
                  <button class="cron-main" @click="emit('edit', job)">
                    <span class="cron-title">{{ displayCronJobTitle(job) }}</span>
                  </button>
                </td>
                <td>
                  <n-tag size="small" type="info" :bordered="false" class="cron-agent-tag">
                    {{ fallbackAgentLabel(job) }}
                  </n-tag>
                </td>
                <td>
                  <n-tag size="small" :type="job.status === 'ACTIVE' ? 'success' : job.status === 'PAUSED' ? 'warning' : 'default'">
                    {{ cronStatusLabel(job.status) }}
                  </n-tag>
                </td>
                <td class="cron-cycle-cell">
                  <div class="cron-cycle-content">
                    <span
                      class="cron-cycle-text"
                      :class="{ 'is-hidden': hoveredJobUid === job.jobUid || menuOpenJobUid === job.jobUid }"
                    >
                      {{ humanizeCronExpression(job.expression) }}
                    </span>
                    <div
                      class="cron-inline-actions"
                      :class="{ visible: hoveredJobUid === job.jobUid || menuOpenJobUid === job.jobUid }"
                    >
                      <n-tooltip trigger="hover">
                        <template #trigger>
                          <n-button size="tiny" text class="icon-action-btn" @click="emit('edit', job)">
                            <template #icon><Pencil :size="15" /></template>
                          </n-button>
                        </template>
                        编辑
                      </n-tooltip>
                      <n-tooltip trigger="hover">
                        <template #trigger>
                          <n-button size="tiny" text class="icon-action-btn" @click="cronJobsStore.runJob(job.jobUid)">
                            <template #icon><Play :size="15" /></template>
                          </n-button>
                        </template>
                        运行
                      </n-tooltip>
                      <n-dropdown
                        trigger="click"
                        :options="moreOptions(job)"
                        @select="(key) => onMoreSelect(job, key)"
                        @update:show="(show) => onMoreVisible(job.jobUid, show)"
                      >
                        <n-button size="tiny" text class="icon-action-btn" aria-label="更多操作">
                          <template #icon><Ellipsis :size="15" /></template>
                        </n-button>
                      </n-dropdown>
                    </div>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="cron-empty">
          <div class="cron-empty-title">{{ t("cron.list.emptyTitle") }}</div>
          <div class="cron-empty-subtitle">{{ t("cron.list.emptySubtitle") }}</div>
          <n-button type="primary" @click="emit('create')">{{ t("cron.list.createFirstTask") }}</n-button>
        </div>
      </div>
    </div>
  </div>

  <n-modal
    v-model:show="showResultModal"
    preset="card"
    style="width: min(860px, 92vw)"
    title="最近执行记录"
    :bordered="false"
  >
    <div class="result-modal-title">{{ resultModalTitle }}</div>
    <n-empty v-if="!resultModalRows.length" description="暂无执行记录" />
    <div v-else class="result-modal-list">
      <div v-for="(item, index) in resultModalRows" :key="`${item.executedTime}-${index}`" class="result-modal-item">
        <div class="result-modal-head">
          <span>{{ formatDateTime(item.executedTime) }}</span>
          <n-tag size="small" :type="item.status === 'FAILED' ? 'error' : 'success'">{{ item.status }}</n-tag>
        </div>
        <div class="result-modal-summary">{{ item.summary || "无摘要" }}</div>
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
.cron-list-header {
  padding: 0;
  border-bottom: 0;
}

.cron-list-header-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.cron-list-header-top > div:first-child {
  flex: 1 1 320px;
  min-width: 220px;
}

.cron-list-header-top > .n-button {
  flex: 0 0 auto;
}

.cron-list-header .panel-subtitle {
  max-width: var(--size-360);
}

.cron-list-header .panel-title {
  font-size: var(--text-title-sm-size);
  line-height: 1.12;
}

.cron-list-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: var(--space-2);
  background: transparent;
}

.cron-scroll-body {
  overflow: hidden;
}

.cron-table-wrap {
  min-height: 0;
  overflow: auto;
}

.cron-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.cron-col-title {
  width: 34%;
}

.cron-col-agent {
  width: 140px;
}

.cron-col-status {
  width: 84px;
}

.cron-col-cycle {
  width: auto;
}

.cron-table-head-cell {
  padding: var(--space-2) var(--space-3);
  border-bottom: var(--size-1) solid var(--color-border-slate-subtle);
  color: var(--text-muted);
  font-size: var(--text-caption-size);
  font-weight: 500;
  text-align: left;
}

.cron-table-head-row .cron-table-head-cell:first-child {
  padding-left: var(--space-3);
}

.cron-job-row td {
  padding: var(--space-2_5) var(--space-3);
  border-bottom: var(--size-1) solid var(--color-border-slate-subtle);
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
  vertical-align: middle;
  transition: background-color 0.16s ease;
}

.cron-job-row td:first-child {
  padding-left: var(--space-3);
}

.cron-job-row:hover td {
  background: color-mix(in srgb, var(--color-overlay-brand-16) 48%, transparent);
}

.cron-agent-tag :deep(.n-tag) {
  background: color-mix(in srgb, var(--color-brand-500) 16%, transparent);
  color: var(--color-text-secondary);
}

.cron-cycle-cell {
  min-width: 0;
}

.cron-cycle-content {
  position: relative;
}

.cron-cycle-text {
  display: block;
  transition: opacity 0.12s ease;
}

.cron-cycle-text.is-hidden {
  opacity: 0;
}

.cron-inline-actions {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: var(--space-2);
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.12s ease;
}

.cron-inline-actions.visible {
  opacity: 1;
  pointer-events: auto;
}

.icon-action-btn :deep(.n-button__border),
.icon-action-btn :deep(.n-button__state-border) {
  opacity: 0 !important;
}

.icon-action-btn :deep(.n-button__state) {
  transition: background-color 0.18s ease;
}

.icon-action-btn:hover :deep(.n-button__state) {
  background-color: color-mix(in srgb, var(--color-overlay-brand-16) 75%, transparent);
}

.icon-action-btn-danger:hover :deep(.n-button__state) {
  background-color: color-mix(in srgb, var(--color-danger-500) 18%, transparent);
}

.cron-main {
  flex: 1;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.cron-title {
  font-size: var(--text-body-size);
  font-weight: 600;
}

.cron-empty {
  display: flex;
  min-height: var(--size-220);
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: var(--space-2_5);
  padding: var(--space-2) var(--space-1_5);
}

.cron-empty-title {
  font-size: var(--text-title-sm-size);
  font-weight: 600;
  color: var(--color-text-heading);
}

.cron-empty-subtitle {
  max-width: var(--size-320);
  color: var(--color-text-cool-gray);
  font-size: var(--text-body-size);
  line-height: 1.6;
}

.result-modal-title {
  margin-bottom: var(--space-2_5);
  color: var(--color-text-secondary);
}

.result-modal-list {
  max-height: min(62vh, 620px);
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-2_5);
}

.result-modal-item {
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-md);
  padding: var(--space-2_5) var(--space-3);
}

.result-modal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
}

.result-modal-summary {
  margin-top: var(--space-2);
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 960px) {
  .cron-table-wrap {
    overflow: auto;
  }
}
</style>
