<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NCheckbox, NFlex, NTag } from "naive-ui";
import { useCronJobsStore } from "@/stores/cronJobs";
import type { CronJob } from "@/types/api";
import { getSortLocale } from "@/i18n";
import { cronStatusLabel, displayCronJobTitle, fallbackAgentLabel, formatDateTime } from "@/utils/format";

const emit = defineEmits<{
  (event: "create"): void;
}>();

const cronJobsStore = useCronJobsStore();
const { t } = useI18n();

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

</script>

<template>
  <div class="panel">
    <div class="panel-header cron-list-header">
      <div class="panel-title ui-title-xl">{{ t("cron.list.title") }}</div>
      <div class="panel-subtitle ui-subtitle">{{ t("cron.list.subtitle") }}</div>
    </div>
    <div class="panel-body cron-list-panel">
      <n-flex justify="space-between" align="center">
        <n-flex align="center">
          <n-button type="primary" @click="emit('create')">{{ t("cron.list.createTask") }}</n-button>
          <span v-if="cronJobsStore.bulkMode" class="cron-bulk-selected">{{ t("cron.list.selectedCount", { count: cronJobsStore.selectedBulkJobUids.length }) }}</span>
        </n-flex>
        <n-flex v-if="cronJobsStore.bulkMode" :size="8">
          <n-button type="error" secondary @click="cronJobsStore.batchDeleteSelected()">{{ t("cron.list.deleteSelected") }}</n-button>
          <n-button tertiary @click="cronJobsStore.clearBulkMode()">{{ t("common.cancel") }}</n-button>
        </n-flex>
        <n-button v-else secondary strong @click="cronJobsStore.toggleBulkMode()">
          {{ t("cron.list.bulkManage") }}
        </n-button>
      </n-flex>

      <div class="scroll-area">
        <div v-if="cronJobsStore.jobs.length" class="cron-groups">
          <section v-for="group in groupedJobs" :key="group.label" class="cron-group">
            <header class="cron-group-header">
              <div>
                <div class="cron-group-title">{{ group.label }}</div>
                <div class="cron-group-count">{{ t("cron.list.jobCount", { count: group.jobs.length }) }}</div>
              </div>
            </header>
            <div
              v-for="job in group.jobs"
              :key="job.jobUid"
              class="cron-row"
              :class="{ active: job.jobUid === cronJobsStore.selectedJobUid }"
            >
              <n-checkbox
                v-if="cronJobsStore.bulkMode"
                :checked="cronJobsStore.selectedBulkJobUids.includes(job.jobUid)"
                @update:checked="cronJobsStore.toggleBulkSelection(job.jobUid, $event)"
              />
              <button class="cron-main" @click="cronJobsStore.selectJob(job.jobUid)">
                <div class="cron-title">{{ displayCronJobTitle(job) }}</div>
                <div v-if="job.status !== 'PAUSED' && job.nextRunTime" class="cron-next">
                  {{ t("cron.list.nextRunAt", { time: formatDateTime(job.nextRunTime) }) }}
                </div>
              </button>
              <n-tag size="small" :type="job.status === 'ACTIVE' ? 'success' : job.status === 'PAUSED' ? 'warning' : 'default'">
                {{ cronStatusLabel(job.status) }}
              </n-tag>
            </div>
          </section>
        </div>
        <div v-else class="cron-empty">
          <div class="cron-empty-title">{{ t("cron.list.emptyTitle") }}</div>
          <div class="cron-empty-subtitle">{{ t("cron.list.emptySubtitle") }}</div>
          <n-button type="primary" @click="emit('create')">{{ t("cron.list.createFirstTask") }}</n-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cron-list-header {
  padding: 0;
  border-bottom: 0;
}

.cron-list-header .panel-subtitle {
  max-width: var(--size-360);
}

.cron-list-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: var(--space-4);
  background: transparent;
}

.cron-groups {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--size-2) var(--size-2) var(--space-1_5);
}

.cron-group {
  border: var(--size-1) solid var(--color-border-slate-soft);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-surface-soft);
  box-shadow: none;
}

.cron-group-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-3_5);
  background: var(--color-bg-surface-mute);
  border-bottom: var(--size-1) solid var(--color-border-slate-subtle);
}

.cron-group-title {
  font-size: var(--size-15);
  font-weight: 600;
  color: var(--color-text-heading);
}

.cron-group-count {
  margin-top: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.cron-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin: var(--space-2) var(--space-2_5);
  padding: var(--space-3) var(--space-3);
  border: var(--size-1) solid var(--color-border-slate-light);
  border-radius: var(--radius-m);
  background: var(--color-bg-surface-soft);
}

.cron-row.active {
  border-color: var(--color-border-brand-light);
  background: var(--color-overlay-brand-08);
  box-shadow: inset 0 0 0 var(--size-1) var(--color-border-brand-inner);
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
  font-size: var(--font-size-md);
  font-weight: 600;
}

.cron-next {
  margin-top: var(--space-1_5);
  font-size: var(--font-size-xs);
  color: var(--text-muted);
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
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text-heading);
}

.cron-empty-subtitle {
  max-width: var(--size-320);
  color: var(--color-text-cool-gray);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.cron-bulk-selected {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>
