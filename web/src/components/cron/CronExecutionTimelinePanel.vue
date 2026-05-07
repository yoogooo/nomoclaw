<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { LoaderCircle } from "lucide-vue-next";
import { NEmpty, NTag } from "naive-ui";
import { useRouter } from "vue-router";
import { useCronJobsStore } from "@/stores/cronJobs";
import { message } from "@/discrete";
import { displayCronJobTitle, formatRelativeTime } from "@/utils/format";

const cronJobsStore = useCronJobsStore();
const router = useRouter();
const { t } = useI18n();

const RUNNING_EXECUTION_STATUSES = new Set(["RUNNING", "IN_PROGRESS", "WAITING_APPROVAL"]);

function normalizeId(value?: string | null) {
  const normalized = String(value || "").trim();
  return normalized || null;
}

const runningJobs = computed(() => {
  const finishedExecutionUids = new Set(
    cronJobsStore.recentGlobalResults
      .map((item) => String(item.executionUid || "").trim())
      .filter((uid) => uid.length > 0)
  );
  return cronJobsStore.jobs
    .filter((job) => {
      const executionUid = String(job.currentExecutionUid || "").trim();
      if (!executionUid) {
        return false;
      }
      const normalizedStatus = String(job.currentExecutionStatus || "").toUpperCase();
    if (RUNNING_EXECUTION_STATUSES.has(normalizedStatus)) {
      return true;
    }
    if (String(job.triggerState || "").toUpperCase() === "BLOCKED") {
      return true;
    }
      // Fallback for status-sync delay: keep it in running list until it appears in completed results.
      return !finishedExecutionUids.has(executionUid);
    })
    .map((job) => ({
      jobUid: job.jobUid,
      jobTitle: displayCronJobTitle(job),
      triggerState: job.triggerState || "UNKNOWN",
      executionStatus: String(job.currentExecutionStatus || "").toUpperCase(),
      executionUid: normalizeId(job.currentExecutionUid),
      conversationUid: normalizeId(job.currentConversationUid),
      messageUid: normalizeId(job.currentMessageUid),
      startedTime: normalizeId(job.currentExecutionStartedTime)
    }));
});

const completedResults = computed(() => [...cronJobsStore.recentGlobalResults]
  .sort((a, b) => new Date(b.executedTime).getTime() - new Date(a.executedTime).getTime()));

function completedTagType(status?: string | null) {
  return String(status || "").toUpperCase() === "FAILED" ? "error" : "success";
}

function completedStatusText(status?: string | null) {
  return String(status || "").toUpperCase() === "FAILED" ? t("common.failed") : t("common.success");
}

function openExecution(executionUid?: string | null, conversationUid?: string | null, messageUid?: string | null) {
  const normalizedExecutionUid = normalizeId(executionUid);
  const normalizedConversationUid = normalizeId(conversationUid);
  const normalizedMessageUid = normalizeId(messageUid);
  if (!normalizedExecutionUid) {
    return;
  }
  void cronJobsStore.markExecutionRead(normalizedExecutionUid);
  if (normalizedConversationUid) {
    void router.push({
      path: `/cron/executions/${normalizedExecutionUid}/chat`,
      query: {
        conversationUid: normalizedConversationUid,
        ...(normalizedMessageUid ? { messageUid: normalizedMessageUid } : {})
      }
    });
    return;
  }
  void router.push(`/cron/executions/${normalizedExecutionUid}/chat`);
}

function openRunningExecution(item: { executionUid?: string | null; conversationUid?: string | null; messageUid?: string | null }) {
  const executionUid = normalizeId(item.executionUid);
  const conversationUid = normalizeId(item.conversationUid);
  if (executionUid && conversationUid) {
    void openExecution(executionUid, conversationUid, normalizeId(item.messageUid));
    return;
  }
  if (executionUid) {
    message.warning("执行会话准备中，请稍后重试。");
    return;
  }
  message.info("当前执行标识尚未就绪，请稍后重试。");
}

function openCompletedExecution(item: { executionUid?: string | null; conversationUid?: string | null; messageUid?: string | null }) {
  void openExecution(item.executionUid, item.conversationUid, item.messageUid);
}

function runningTimeText(startedTime?: string | null) {
  const normalized = normalizeId(startedTime);
  if (!normalized) {
    return "刚刚开始";
  }
  return formatRelativeTime(normalized);
}

function runningStatusText(status?: string | null) {
  const normalized = String(status || "").toUpperCase();
  if (normalized === "WAITING_APPROVAL") {
    return "待人工审核";
  }
  if (normalized === "RUNNING" || normalized === "IN_PROGRESS" || !normalized) {
    return "运行中";
  }
  if (normalized === "FAILED") {
    return "失败";
  }
  if (normalized === "CANCELED") {
    return "已取消";
  }
  return normalized.toLowerCase();
}

function runningStatusTagType(status?: string | null) {
  const normalized = String(status || "").toUpperCase();
  if (normalized === "WAITING_APPROVAL") {
    return "warning";
  }
  if (normalized === "FAILED" || normalized === "CANCELED") {
    return "error";
  }
  return "warning";
}
</script>

<template>
  <div class="cron-execution-sections">
    <section v-if="runningJobs.length" class="panel cron-section">
      <div class="panel-header cron-section-header">
        <h3 class="cron-section-title">{{ t("cron.execution.runningSectionTitle") }}</h3>
      </div>
      <div class="panel-body">
        <div class="execution-list">
          <div
            v-for="item in runningJobs"
            :key="item.jobUid"
            class="execution-item running-item"
            :class="{ 'is-clickable': !!(item.executionUid && item.conversationUid) }"
            :role="item.executionUid && item.conversationUid ? 'button' : undefined"
            :tabindex="item.executionUid && item.conversationUid ? 0 : undefined"
            @click="openRunningExecution(item)"
            @keydown.enter="openRunningExecution(item)"
            @keydown.space.prevent="openRunningExecution(item)"
          >
            <div class="running-icon-wrap" aria-hidden="true">
              <LoaderCircle :size="14" class="running-icon" />
            </div>
            <div class="execution-main">
              <div class="execution-title-row">
                <div class="execution-title">{{ item.jobTitle }}</div>
              </div>
            </div>
            <div class="execution-side">
              <n-tag :type="runningStatusTagType(item.executionStatus)" size="small">
                {{ runningStatusText(item.executionStatus) }}
              </n-tag>
              <div class="execution-time running-time">{{ runningTimeText(item.startedTime) }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="panel cron-section">
      <div class="panel-header cron-section-header">
        <h3 class="cron-section-title">{{ t("cron.execution.completedSectionTitle") }}</h3>
      </div>
      <div class="panel-body">
        <n-empty v-if="!completedResults.length" :description="t('cron.execution.noCompletedTasks')" />
        <div v-else class="execution-list">
          <div
            v-for="(item, index) in completedResults"
            :key="`${item.executionUid || item.executedTime}-${index}`"
            class="execution-item completed-item"
            :class="{ 'is-clickable': !!item.executionUid }"
            :role="item.executionUid ? 'button' : undefined"
            :tabindex="item.executionUid ? 0 : undefined"
            @click="openCompletedExecution(item)"
            @keydown.enter="openCompletedExecution(item)"
            @keydown.space.prevent="openCompletedExecution(item)"
          >
            <div class="execution-main">
              <div class="execution-title-row">
                <span v-if="item.unread" class="unread-dot" aria-hidden="true" />
                <div class="execution-title">{{ item.jobTitle || t("format.fallbackNoName") }}</div>
                <div class="completed-meta">
                  <n-tag :type="completedTagType(item.status)" size="small">
                    {{ completedStatusText(item.status) }}
                  </n-tag>
                  <div class="execution-time">{{ formatRelativeTime(item.executedTime) }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.cron-execution-sections {
  display: grid;
  gap: var(--space-4);
}

.cron-section {
  padding: 0 0 var(--space-4);
}

.panel-header.cron-section-header {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: var(--size-48);
  min-height: var(--size-48);
  padding: 0 var(--space-5_5);
}

.cron-section-title {
  display: flex;
  align-items: center;
  height: 100%;
  font-size: var(--text-title-sm-size);
  line-height: 1;
  margin: 0;
  padding-top: 0.12em;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--color-text-heading-deep);
}

.execution-list {
  display: grid;
  gap: var(--space-3);
}

.execution-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: center;
  border: var(--size-1) solid var(--color-border-panel);
  border-radius: var(--radius-lg);
  padding: var(--space-3);
  background: var(--color-bg-surface);
}

.completed-item {
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
}

.completed-item.is-clickable {
  cursor: pointer;
}

.completed-item.is-clickable:hover {
  border-color: var(--color-border-brand-light);
  background: color-mix(in srgb, var(--color-bg-surface) 92%, var(--color-brand-600) 8%);
}

.running-item.is-clickable {
  cursor: pointer;
}

.running-item.is-clickable:hover {
  border-color: var(--color-border-brand-light);
  background: color-mix(in srgb, var(--color-bg-surface) 92%, var(--color-brand-600) 8%);
}

.completed-item.is-clickable:focus-visible {
  outline: var(--size-2) solid var(--color-brand-500);
  outline-offset: var(--size-2);
}

.running-item.is-clickable:focus-visible {
  outline: var(--size-2) solid var(--color-brand-500);
  outline-offset: var(--size-2);
}

.execution-dot {
  width: var(--size-12);
  height: var(--size-12);
  border-radius: var(--radius-pill);
  background: var(--color-text-muted);
}

.spinning-dot {
  border: var(--size-2) solid var(--color-border-brand-light);
  border-top-color: var(--color-brand-600);
  background: transparent;
  animation: spin 1s linear infinite;
}

.running-dot {
  background: var(--color-brand-600);
  box-shadow: 0 0 0 0 color-mix(in srgb, var(--color-brand-600) 55%, transparent);
  animation: running-pulse 1.5s ease-out infinite;
}

.running-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--size-16);
  height: var(--size-16);
  color: var(--color-brand-600);
}

.running-icon {
  animation: spin 1s linear infinite;
}

.success-dot {
  background: var(--color-success-600);
}

.error-dot {
  background: var(--color-danger-500);
}

.unread-dot {
  width: var(--size-8);
  height: var(--size-8);
  border-radius: var(--radius-pill);
  background: var(--color-brand-600);
  flex: 0 0 auto;
}

.execution-main {
  min-width: 0;
}

.execution-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  width: 100%;
}

.execution-title {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--text-body-size);
  font-weight: 650;
  color: var(--color-text-heading);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.execution-uid {
  max-width: var(--size-240);
  border: var(--size-1) solid var(--color-border-panel);
  border-radius: var(--radius-md);
  padding: 0 var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.execution-side {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: nowrap;
  white-space: nowrap;
}

.completed-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
  min-width: 0;
  flex: 0 0 auto;
  white-space: nowrap;
}

.execution-time {
  color: var(--color-text-muted);
  font-size: var(--text-caption-size);
  white-space: nowrap;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .running-item {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: start;
  }

  .execution-side {
    grid-column: 2;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .running-item .execution-title-row {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }

  .completed-item .execution-title-row {
    flex-wrap: wrap;
  }

  .completed-item .completed-meta {
    margin-left: 0;
    width: 100%;
  }

  .execution-uid {
    max-width: 100%;
  }
}

@keyframes running-pulse {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--color-brand-600) 55%, transparent);
  }
  70% {
    box-shadow: 0 0 0 var(--size-8) color-mix(in srgb, var(--color-brand-600) 0%, transparent);
  }
  100% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--color-brand-600) 0%, transparent);
  }
}
</style>
