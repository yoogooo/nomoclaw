<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NCard, NCollapse, NCollapseItem, NEmpty, NFlex, NModal, NPopconfirm, NSelect, NSwitch, NTabPane, NTabs, NTag } from "naive-ui";
import CronEditModal from "./CronEditModal.vue";
import { useCronJobsStore } from "@/stores/cronJobs";
import { channelApi } from "@/api/channelApi";
import {
  cronStatusLabel,
  displayCronJobTitle,
  fallbackAgentLabel,
  formatDateTime,
  humanizeCronExpression,
  humanizeTimezone
} from "@/utils/format";
import { renderMarkdown } from "@/utils/markdown";

const cronJobsStore = useCronJobsStore();
const { t } = useI18n();
const showEditModal = ref(false);
const savingSubscriptions = ref(false);
const switchingJobStatus = ref(false);
const selectedChannel = ref<"feishu" | "dingtalk" | null>(null);
const enabledChannelOptions = ref<Array<{ label: string; value: "feishu" | "dingtalk" }>>([]);

const scheduleSummary = computed(() => cronJobsStore.currentJob ? humanizeCronExpression(cronJobsStore.currentJob.expression) : "-");
const nextRunText = computed(() => cronJobsStore.currentJob ? formatDateTime(cronJobsStore.currentJob.nextRunTime) : "-");
const shouldShowNextRun = computed(() =>
  !!cronJobsStore.currentJob
  && cronJobsStore.currentJob.status !== "PAUSED"
  && !!cronJobsStore.currentJob.nextRunTime
);
const deadlineText = computed(() => {
  if (!cronJobsStore.currentJob) return "-";
  return cronJobsStore.currentJob.endAt ? formatDateTime(cronJobsStore.currentJob.endAt) : t("cron.detail.longRunning");
});
const statusText = computed(() => cronJobsStore.currentJob ? cronStatusLabel(cronJobsStore.currentJob.status) : "-");
const recentResults = computed(() => cronJobsStore.currentResults.slice(0, 20));
const previewModalVisible = ref(false);
const previewTitle = ref("");
const previewMarkdown = ref("");
const previewHtml = computed(() => renderMarkdown(previewMarkdown.value || ""));

watch(
  () => [cronJobsStore.currentJob?.jobUid, cronJobsStore.currentSubscriptions],
  () => {
    if (!cronJobsStore.currentJob) {
      selectedChannel.value = null;
      return;
    }
    const first = (cronJobsStore.currentSubscriptions || [])[0];
    selectedChannel.value = first
      ? (first.channel === "dingtalk" ? "dingtalk" : "feishu")
      : null;
  },
  { immediate: true, deep: true }
);

void loadEnabledChannels();

async function loadEnabledChannels() {
  try {
    const config = await channelApi.getChannelConfig();
    const options: Array<{ label: string; value: "feishu" | "dingtalk" }> = [];
    if (config.channels.feishu.enabled) {
      options.push({ label: t("cron.detail.channel.feishu"), value: "feishu" });
    }
    if (config.channels.dingtalk.enabled) {
      options.push({ label: t("cron.detail.channel.dingtalk"), value: "dingtalk" });
    }
    enabledChannelOptions.value = options;
    if (selectedChannel.value && !options.some((item) => item.value === selectedChannel.value)) {
      selectedChannel.value = null;
    }
  } catch {
    enabledChannelOptions.value = [];
    selectedChannel.value = null;
  }
}

function addSubscription() {
  if (!enabledChannelOptions.value.length) {
    return;
  }
  selectedChannel.value = enabledChannelOptions.value[0].value;
}

async function saveSubscriptions() {
  if (!cronJobsStore.currentJob) {
    return;
  }
  const payload = selectedChannel.value
    ? [{ channel: selectedChannel.value, target: "", enabled: true }]
    : [];
  savingSubscriptions.value = true;
  try {
    await cronJobsStore.updateSubscriptions(cronJobsStore.currentJob.jobUid, payload);
  } finally {
    savingSubscriptions.value = false;
  }
}

const isCurrentJobEnabled = computed(() => cronJobsStore.currentJob?.status === "ACTIVE");
const canToggleCurrentJob = computed(() => {
  const status = cronJobsStore.currentJob?.status;
  return status === "ACTIVE" || status === "PAUSED";
});

async function toggleCurrentJobStatus(enabled: boolean) {
  if (!cronJobsStore.currentJob || switchingJobStatus.value || !canToggleCurrentJob.value) {
    return;
  }
  switchingJobStatus.value = true;
  try {
    if (enabled) {
      await cronJobsStore.resumeJob(cronJobsStore.currentJob.jobUid);
    } else {
      await cronJobsStore.pauseJob(cronJobsStore.currentJob.jobUid);
    }
  } finally {
    switchingJobStatus.value = false;
  }
}

function executionStatusText(status: string | null | undefined) {
  const normalized = (status || "").toUpperCase();
  if (normalized === "COMPLETED") return t("common.success");
  if (normalized === "FAILED") return t("common.failed");
  return normalized || t("common.unknown");
}

function openResultPreview(executedTime: string, content: string) {
  previewTitle.value = t("cron.detail.previewTitle", { time: formatDateTime(executedTime) });
  previewMarkdown.value = content;
  previewModalVisible.value = true;
}
</script>

<template>
  <div class="panel detail-panel">
    <div class="panel-header detail-header">
      <div class="ui-kicker">DETAIL</div>
      <div class="panel-title ui-title-xl">{{ t("cron.detail.title") }}</div>
      <div class="panel-subtitle ui-subtitle">
        {{ cronJobsStore.currentJob ? `${displayCronJobTitle(cronJobsStore.currentJob)} · ${fallbackAgentLabel(cronJobsStore.currentJob)}` : t("cron.detail.selectOneHint") }}
      </div>
    </div>
    <div class="panel-body scroll-area">
      <div v-if="!cronJobsStore.currentJob" class="detail-empty">
        <div class="detail-empty-title">{{ t("cron.detail.emptyTitle") }}</div>
        <div class="detail-empty-subtitle">{{ t("cron.detail.emptySubtitle") }}</div>
        <ol class="detail-empty-steps">
          <li>{{ t("cron.detail.emptyStep1") }}</li>
          <li>{{ t("cron.detail.emptyStep2") }}</li>
          <li>{{ t("cron.detail.emptyStep3") }}</li>
        </ol>
      </div>
      <template v-else>
        <n-flex justify="space-between" align="flex-start" class="detail-toolbar">
          <div>
            <div class="detail-title">{{ displayCronJobTitle(cronJobsStore.currentJob) }}</div>
            <div class="detail-subtitle">
              <template v-if="shouldShowNextRun">
                {{ fallbackAgentLabel(cronJobsStore.currentJob) }} · {{ t("cron.list.nextRunAt", { time: formatDateTime(cronJobsStore.currentJob.nextRunTime) }) }}
              </template>
              <template v-else>
                {{ fallbackAgentLabel(cronJobsStore.currentJob) }}
              </template>
            </div>
          </div>
          <div class="job-status-switch">
            <span class="job-status-label">{{ isCurrentJobEnabled ? t("common.enabled") : t("cron.detail.paused") }}</span>
            <n-switch
              :value="isCurrentJobEnabled"
              :loading="switchingJobStatus"
              :disabled="!canToggleCurrentJob"
              @update:value="toggleCurrentJobStatus"
            />
          </div>
        </n-flex>

        <n-flex :size="12" class="detail-actions detail-global-actions">
          <n-popconfirm
            :positive-text="t('cron.detail.runNow')"
            :negative-text="t('common.cancel')"
            @positive-click="cronJobsStore.runJob(cronJobsStore.currentJob.jobUid)"
          >
            <template #trigger>
              <n-button type="primary">{{ t("cron.detail.runNow") }}</n-button>
            </template>
            {{ t("cron.detail.runNowConfirm") }}
          </n-popconfirm>
          <n-button secondary @click="showEditModal = true">{{ t("cron.detail.editTask") }}</n-button>
          <n-button type="error" secondary @click="cronJobsStore.confirmDeleteJob(cronJobsStore.currentJob)">{{ t("cron.detail.deleteTask") }}</n-button>
        </n-flex>

        <n-tabs v-model:value="cronJobsStore.selectedTab" type="line" animated>
          <n-tab-pane name="config" :tab="t('cron.detail.tabConfig')">
            <n-card embedded>
              <div class="job-overview-grid">
                <div class="job-overview-item">
                  <div class="job-overview-label">{{ t("cron.detail.frequency") }}</div>
                  <div class="job-overview-value">{{ scheduleSummary }}</div>
                </div>
                <div v-if="shouldShowNextRun" class="job-overview-item">
                  <div class="job-overview-label">{{ t("cron.detail.nextRun") }}</div>
                  <div class="job-overview-value emph">{{ nextRunText }}</div>
                </div>
                <div class="job-overview-item">
                  <div class="job-overview-label">{{ t("cron.detail.currentStatus") }}</div>
                  <div class="job-overview-value">
                    <n-tag size="small" :type="isCurrentJobEnabled ? 'success' : 'warning'">{{ statusText }}</n-tag>
                  </div>
                </div>
                <div class="job-overview-item">
                  <div class="job-overview-label">{{ t("cron.detail.endAt") }}</div>
                  <div class="job-overview-value">{{ deadlineText }}</div>
                </div>
              </div>

              <div class="job-section">
                <div class="job-overview-label">{{ t("cron.form.taskContent") }}</div>
                <div class="job-content-card">{{ cronJobsStore.currentJob.taskContent || t("cron.detail.noTaskContent") }}</div>
              </div>

              <div class="job-section">
                <div class="job-overview-label">{{ t("cron.form.timezone") }}</div>
                <div class="job-content-card">{{ humanizeTimezone(cronJobsStore.currentJob.timezone) }}</div>
              </div>

              <n-collapse class="job-tech-collapse">
                <n-collapse-item name="tech" :title="t('cron.detail.systemInfo')">
                  <div class="job-tech-grid">
                    <div class="job-tech-item">
                      <span class="job-tech-key">{{ t("cron.detail.cronExpression") }}</span>
                      <span class="job-tech-value">{{ cronJobsStore.currentJob.expression || "-" }}</span>
                    </div>
                    <div class="job-tech-item">
                      <span class="job-tech-key">Job UID</span>
                      <span class="job-tech-value uid">{{ cronJobsStore.currentJob.jobUid }}</span>
                    </div>
                    <div class="job-tech-item">
                      <span class="job-tech-key">{{ t("cron.detail.triggerState") }}</span>
                      <span class="job-tech-value">{{ cronJobsStore.currentJob.triggerState || "-" }}</span>
                    </div>
                  </div>
                </n-collapse-item>
              </n-collapse>
            </n-card>

            <n-card embedded class="subscription-card">
              <n-flex justify="space-between" align="center" class="subscription-head">
                <div class="detail-title detail-title-sm">{{ t("cron.detail.subscriptionTitle") }}</div>
                <n-flex :size="8">
                  <n-button
                    secondary
                    :disabled="!!selectedChannel || !enabledChannelOptions.length"
                    @click="addSubscription"
                  >
                    {{ t("cron.detail.addSubscription") }}
                  </n-button>
                  <n-button type="primary" :loading="savingSubscriptions" @click="saveSubscriptions">{{ t("cron.detail.saveSubscription") }}</n-button>
                </n-flex>
              </n-flex>
              <div v-if="!enabledChannelOptions.length" class="subscription-empty">
                <n-empty :description="t('cron.detail.noEnabledChannels')" />
              </div>
              <div v-else-if="!selectedChannel" class="subscription-empty">
                <n-empty :description="t('cron.detail.noSubscriptionYet')" />
              </div>
              <div v-else class="subscription-list">
                <div class="subscription-item">
                  <n-select
                    v-model:value="selectedChannel"
                    class="subscription-channel"
                    :options="enabledChannelOptions"
                  />
                  <n-button text type="error" @click="selectedChannel = null">{{ t("common.delete") }}</n-button>
                </div>
              </div>
            </n-card>
          </n-tab-pane>

          <n-tab-pane name="result" :tab="t('cron.detail.tabResult')">
            <n-card embedded>
              <n-empty v-if="!recentResults.length" :description="t('cron.detail.noResults')" />
              <div v-else class="recent-result-list">
                <div
                  v-for="(item, index) in recentResults"
                  :key="`${item.executedTime}-${index}`"
                  class="recent-result-item"
                >
                  <div class="recent-result-head">
                    <div class="recent-result-time">{{ formatDateTime(item.executedTime) }}</div>
                    <n-tag size="small" :type="item.status === 'FAILED' ? 'error' : 'success'">
                      {{ executionStatusText(item.status) }}
                    </n-tag>
                  </div>
                  <div class="recent-result-summary">{{ item.summary || t("cron.detail.noResultSummary") }}</div>
                  <div v-if="item.reportContent" class="recent-result-actions">
                    <n-button
                      text
                      type="primary"
                      @click="openResultPreview(item.executedTime, item.reportContent)"
                    >
                      {{ t("cron.detail.previewResult") }}
                    </n-button>
                    <n-button
                      v-if="item.reportPath"
                      text
                      type="primary"
                      class="recent-result-open"
                      @click="cronJobsStore.openReportFile(item.reportPath)"
                    >
                      {{ t("cron.detail.openReport") }}
                    </n-button>
                  </div>
                  <n-button
                    v-else-if="item.reportPath"
                    text
                    type="primary"
                    class="recent-result-open"
                    @click="cronJobsStore.openReportFile(item.reportPath)"
                  >
                    {{ t("cron.detail.openReport") }}
                  </n-button>
                </div>
              </div>
            </n-card>
          </n-tab-pane>

        </n-tabs>
      </template>
    </div>

    <n-modal
      v-model:show="previewModalVisible"
      preset="card"
      style="width: min(920px, 92vw)"
      :title="previewTitle"
      :bordered="false"
      size="huge"
    >
      <article class="message-html result-preview-content" v-html="previewHtml" />
    </n-modal>

    <CronEditModal
      :show="showEditModal"
      :job="cronJobsStore.currentJob"
      @update:show="showEditModal = $event"
      @submit="cronJobsStore.currentJob && cronJobsStore.updateJob(cronJobsStore.currentJob.jobUid, $event)"
    />
  </div>
</template>

<style scoped>
.detail-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.detail-header {
  padding: 0;
  border-bottom: 0;
}

.detail-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--color-text-primary);
}

.detail-subtitle {
  margin-top: var(--space-2);
  color: var(--color-text-cool-gray);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.recent-result-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.recent-result-item {
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-4);
  background: var(--color-bg-surface);
}

.recent-result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
}

.recent-result-time {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.recent-result-summary {
  margin-top: var(--space-2);
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.recent-result-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2_5);
  margin-top: var(--space-2);
}

.recent-result-open {
  margin-top: 0;
}

.result-preview-content {
  max-height: min(72vh, 780px);
  overflow: auto;
  padding: var(--space-2);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-bg-surface) 96%, var(--color-bg-surface-soft));
}

.detail-empty {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--space-1) 0;
}

.detail-empty-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--color-text-primary);
}

.detail-empty-subtitle {
  margin-top: var(--space-2);
  color: var(--color-text-cool-gray);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.detail-empty-steps {
  margin: var(--space-3_5) 0 0;
  padding-left: var(--space-4_5);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.8;
}

.detail-toolbar {
  margin-bottom: var(--space-4_5);
}

.detail-actions {
  margin-bottom: var(--space-4_5);
}

.job-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3_5);
}

.job-overview-item {
  padding: var(--space-3_5);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--color-bg-surface-soft) 86%, white);
}

.job-overview-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-cool-gray);
}

.job-overview-value {
  margin-top: var(--space-1_5);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.job-overview-value.emph {
  font-size: var(--font-size-md);
  font-weight: 600;
}

.job-section {
  margin-top: var(--space-3_5);
}

.job-content-card {
  margin-top: var(--space-2);
  padding: var(--space-3_5);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--color-bg-surface-soft) 86%, white);
  color: var(--color-text-primary);
  line-height: 1.75;
  white-space: pre-wrap;
}

.job-tech-collapse {
  margin-top: var(--space-3_5);
}

.job-tech-grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-2_5);
}

.job-tech-item {
  display: grid;
  grid-template-columns: 108px 1fr;
  gap: var(--space-2_5);
  align-items: start;
}

.job-tech-key {
  color: var(--color-text-cool-gray);
  font-size: var(--font-size-xs);
}

.job-tech-value {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  word-break: break-all;
}

.job-tech-value.uid {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: var(--font-size-xs);
}

.job-status-switch {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2_5);
}

.job-status-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.detail-title-sm {
  font-size: var(--font-size-md);
}

.subscription-card {
  margin-top: var(--space-4_5);
}

.subscription-head {
  margin-bottom: var(--space-3_5);
}

.subscription-empty {
  padding: var(--space-4) 0;
}

.subscription-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.subscription-item {
  display: grid;
  grid-template-columns: 220px auto;
  align-items: center;
  gap: var(--space-3);
}

.subscription-channel {
  min-width: 120px;
}

@media (max-width: 960px) {
  .job-overview-grid {
    grid-template-columns: 1fr;
  }

  .job-tech-item {
    grid-template-columns: 1fr;
  }

  .subscription-item {
    grid-template-columns: 1fr;
  }
}

.detail-panel :deep(.n-card.n-card--embedded) {
  background: var(--color-bg-surface-soft);
  border: var(--size-1) solid var(--color-border-soft);
  box-shadow: none;
}
</style>
