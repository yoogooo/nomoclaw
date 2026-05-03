<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NDropdown, NEmpty, NFlex, NModal, NTag, NTooltip, type DropdownOption } from "naive-ui";
import { ChevronRight, Ellipsis, Pencil, Play } from "lucide-vue-next";
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
const collapsedGroups = ref<Record<string, boolean>>({});
const hoveredJobUid = ref<string | null>(null);
const menuOpenJobUid = ref<string | null>(null);
const showResultModal = ref(false);
const resultModalTitle = ref("");
const resultModalRows = ref<Array<{ executedTime: string; status: string; summary: string }>>([]);

const groupedJobs = computed(() => {
  const order = new Map<string, number>();
  let index = 0;
  cronJobsStore.agentGroups.forEach((group) => {
    group.agents.forEach((agent) => {
      if (!order.has(agent.agentUid)) {
        order.set(agent.agentUid, index++);
      }
    });
  });

  const groups = new Map<string, { label: string; sortIndex: number; jobs: CronJob[] }>();

  cronJobsStore.jobs.forEach((job) => {
    const key = job.agentUid || "__unbound__";
    if (!groups.has(key)) {
      groups.set(key, {
        label: fallbackAgentLabel(job),
        sortIndex: order.get(job.agentUid) ?? Number.MAX_SAFE_INTEGER,
        jobs: []
      });
    }
    groups.get(key)!.jobs.push(job);
  });

  return Array.from(groups.values()).sort((left, right) => {
    if (left.sortIndex !== right.sortIndex) {
      return left.sortIndex - right.sortIndex;
    }
    return left.label.localeCompare(right.label, getSortLocale());
  });
});

function toggleGroup(groupLabel: string) {
  collapsedGroups.value[groupLabel] = !collapsedGroups.value[groupLabel];
}

function isGroupCollapsed(groupLabel: string) {
  return !!collapsedGroups.value[groupLabel];
}

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
            <tbody>
              <template v-for="group in groupedJobs" :key="group.label">
                <tr class="cron-group-row">
                  <td colspan="3">
                    <button class="cron-group-header" @click="toggleGroup(group.label)">
                      <span class="cron-group-toggle" :class="{ collapsed: isGroupCollapsed(group.label) }">
                        <ChevronRight :size="18" />
                      </span>
                      <span class="cron-group-title">{{ group.label }}</span>
                      <span class="cron-group-count">{{ t("cron.list.jobCount", { count: group.jobs.length }) }}</span>
                    </button>
                  </td>
                </tr>
                <tr
                  v-for="job in group.jobs"
                  v-show="!isGroupCollapsed(group.label)"
                  :key="job.jobUid"
                  class="cron-job-row"
                  :class="{ active: job.jobUid === cronJobsStore.selectedJobUid }"
                  @mouseenter="hoveredJobUid = job.jobUid"
                  @mouseleave="onJobMouseLeave(job.jobUid)"
                >
                  <td>
                    <button class="cron-main" @click="emit('edit', job)">
                      <span class="cron-title">{{ displayCronJobTitle(job) }}</span>
                    </button>
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
              </template>
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
  align-items: flex-start;
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
  min-width: 760px;
  border-collapse: collapse;
  table-layout: fixed;
}

.cron-group-row td {
  padding: 0;
  border-bottom: var(--size-1) solid var(--color-border-slate-subtle);
}

.cron-group-header {
  display: flex;
  width: 100%;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 0;
  background: transparent;
  color: var(--color-text-heading);
  cursor: pointer;
  text-align: left;
}

.cron-group-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  transform: rotate(90deg);
  transition: transform 0.16s ease;
}

.cron-group-toggle.collapsed {
  transform: rotate(0deg);
}

.cron-group-title {
  font-size: var(--text-title-sm-size);
  font-weight: 600;
  color: var(--color-text-heading);
}

.cron-group-count {
  margin-top: var(--space-1);
  font-size: var(--text-caption-size);
  color: var(--text-muted);
}

.cron-job-row td {
  padding: var(--space-2_5) var(--space-3);
  border-bottom: var(--size-1) solid var(--color-border-slate-subtle);
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
  vertical-align: middle;
  transition: background-color 0.16s ease;
}

.cron-job-row td:nth-child(1) {
  width: 46%;
}

.cron-job-row td:nth-child(2) {
  width: 16%;
}

.cron-job-row td:nth-child(3) {
  width: 38%;
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

.cron-job-row.active td {
  border-color: var(--color-overlay-slate-28);
  background: var(--color-overlay-slate-16);
}

.cron-job-row:hover td {
  background: var(--color-overlay-slate-16);
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
