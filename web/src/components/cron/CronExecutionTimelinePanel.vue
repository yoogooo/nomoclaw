<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { ChevronRight, LoaderCircle } from "lucide-vue-next";
import { NEmpty, NTag } from "naive-ui";
import { useRouter } from "vue-router";
import { useCronJobsStore } from "@/stores/cronJobs";
import { message } from "@/discrete";
import { displayCronJobTitle, formatRelativeTime } from "@/utils/format";

const cronJobsStore = useCronJobsStore();
const router = useRouter();
const { t } = useI18n();
const COMPLETED_VISIBLE_LIMIT = 10;
const FINISHED_EXECUTION_STATUSES = new Set(["COMPLETED", "FAILED", "CANCELED"]);

function normalizeId(value?: string | null) {
  const normalized = String(value || "").trim();
  return normalized || null;
}

const runningJobs = computed(() => {
  return cronJobsStore.runningGlobalResults.map((item, index) => ({
    rowKey: normalizeId(item.executionUid) || normalizeId(item.jobUid) || `running-${index}`,
    jobUid: item.jobUid,
    agentUid: normalizeId(item.agentUid),
    jobTitle: item.jobTitle || t("format.fallbackNoName"),
    executionStatus: String(item.status || "").toUpperCase(),
    executionUid: normalizeId(item.executionUid),
    conversationUid: normalizeId(item.conversationUid),
    messageUid: normalizeId(item.messageUid),
    startedTime: normalizeId(item.executedTime)
  }));
});

const completedResults = computed(() => [...cronJobsStore.recentGlobalResults]
  .filter((item) => FINISHED_EXECUTION_STATUSES.has(String(item.status || "").toUpperCase()))
  .sort((a, b) => new Date(b.executedTime).getTime() - new Date(a.executedTime).getTime()));
const completedVisibleResults = computed(() => completedResults.value.slice(0, COMPLETED_VISIBLE_LIMIT));

function completedTagType(status?: string | null) {
  return String(status || "").toUpperCase() === "FAILED" ? "error" : "success";
}

function completedStatusText(status?: string | null) {
  return String(status || "").toUpperCase() === "FAILED" ? t("common.failed") : t("common.success");
}

function openExecution(params: {
  executionUid?: string | null;
  conversationUid?: string | null;
  messageUid?: string | null;
  agentUid?: string | null;
  jobUid?: string | null;
}) {
  const normalizedExecutionUid = normalizeId(params.executionUid);
  const normalizedConversationUid = normalizeId(params.conversationUid);
  const normalizedMessageUid = normalizeId(params.messageUid);
  const normalizedAgentUid = normalizeId(params.agentUid);
  const normalizedJobUid = normalizeId(params.jobUid);
  if (!normalizedExecutionUid) {
    return;
  }
  void cronJobsStore.markExecutionRead(normalizedExecutionUid);
  if (normalizedJobUid) {
    void cronJobsStore.selectJob(normalizedJobUid);
  }
  if (!normalizedConversationUid) {
    message.warning("执行会话准备中，请稍后重试。");
    return;
  }
  void router.push({
    path: "/",
    query: {
      source: "cron",
      executionUid: normalizedExecutionUid,
      conversationUid: normalizedConversationUid,
      ...(normalizedMessageUid ? { messageUid: normalizedMessageUid } : {}),
      ...(normalizedAgentUid ? { agentUid: normalizedAgentUid } : {}),
      ...(normalizedJobUid ? { jobUid: normalizedJobUid } : {})
    }
  });
}

function openRunningExecution(item: {
  executionUid?: string | null;
  conversationUid?: string | null;
  messageUid?: string | null;
  agentUid?: string | null;
  jobUid?: string | null;
}) {
  const executionUid = normalizeId(item.executionUid);
  const conversationUid = normalizeId(item.conversationUid);
  if (executionUid && conversationUid) {
    openExecution(item);
    return;
  }
  if (executionUid) {
    message.warning("执行会话准备中，请稍后重试。");
    return;
  }
  message.info("当前执行标识尚未就绪，请稍后重试。");
}

function openCompletedExecution(item: {
  executionUid?: string | null;
  conversationUid?: string | null;
  messageUid?: string | null;
  agentUid?: string | null;
  jobUid?: string | null;
}) {
  openExecution(item);
}

function openHistoryPage() {
  void router.push("/cron/executions/history");
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
    return t("cron.execution.statusWaitingApproval");
  }
  if (normalized === "RUNNING" || !normalized) {
    return t("cron.execution.statusRunning");
  }
  if (normalized === "FAILED") {
    return t("cron.execution.statusFailed");
  }
  if (normalized === "CANCELED") {
    return t("cron.execution.statusCanceled");
  }
  return normalized.toLowerCase();
}

function runningStatusTagType(status?: string | null) {
  const normalized = String(status || "").toUpperCase();
  if (normalized === "RUNNING" || !normalized) {
    return "success";
  }
  if (normalized === "WAITING_APPROVAL") {
    return "warning";
  }
  if (normalized === "FAILED" || normalized === "CANCELED") {
    return "error";
  }
  return "success";
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
            :key="item.rowKey"
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

    <section class="panel cron-section completed-section">
      <div class="panel-header cron-section-header with-action">
        <h3 class="cron-section-title">{{ t("cron.execution.completedSectionTitle") }}</h3>
        <button
          type="button"
          class="header-action-btn"
          @click="openHistoryPage"
        >
          <span>{{ t("cron.execution.viewAllCompleted") }}</span>
          <ChevronRight :size="14" />
        </button>
      </div>
      <div class="panel-body">
        <n-empty v-if="!completedResults.length" :description="t('cron.execution.noCompletedTasks')" />
        <div v-else class="completed-table-wrap">
          <table class="completed-table">
            <colgroup>
              <col class="completed-col-title">
              <col class="completed-col-agent">
              <col class="completed-col-status">
              <col class="completed-col-time">
            </colgroup>
            <tbody>
              <tr
                v-for="(item, index) in completedVisibleResults"
                :key="`${item.executionUid || item.executedTime}-${index}`"
                class="completed-table-row"
                :class="{ 'is-clickable': !!item.executionUid }"
                :role="item.executionUid ? 'button' : undefined"
                :tabindex="item.executionUid ? 0 : undefined"
                @click="openCompletedExecution(item)"
                @keydown.enter="openCompletedExecution(item)"
                @keydown.space.prevent="openCompletedExecution(item)"
              >
                <td class="completed-cell completed-cell-title">
                  <span class="unread-dot" :class="{ 'is-hidden': !item.unread }" aria-hidden="true" />
                  <span class="execution-title">{{ item.jobTitle || t("format.fallbackNoName") }}</span>
                </td>
                <td class="completed-cell completed-cell-agent">
                  <n-tag size="small" type="info" :bordered="false" class="completed-agent-tag">
                    {{ item.agentDisplayName || t("format.fallbackNoAgent") }}
                  </n-tag>
                </td>
                <td class="completed-cell completed-cell-status">
                  <n-tag :type="completedTagType(item.status)" size="small">
                    {{ completedStatusText(item.status) }}
                  </n-tag>
                </td>
                <td class="completed-cell completed-cell-time">{{ formatRelativeTime(item.executedTime) }}</td>
              </tr>
            </tbody>
          </table>
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

.completed-section .panel-body {
  padding-top: 0;
  padding-bottom: 0;
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
.panel-header.cron-section-header.with-action {
  justify-content: space-between;
}

.header-action-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
  cursor: pointer;
  transition: color 0.16s ease;
}

.header-action-btn:hover {
  color: var(--color-brand-400);
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

.running-item {
  gap: var(--space-2);
}

.running-item.is-clickable {
  cursor: pointer;
}

.running-item.is-clickable:hover {
  border-color: var(--color-border-panel);
  background: var(--color-bg-soft-hover);
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
  color: #0f8f5c;
}

.running-icon {
  animation: spin 1s linear infinite;
}

:root[data-theme="dark"] .running-icon-wrap {
  color: #3dffb5;
}

.success-dot {
  background: var(--color-success-600);
}

.error-dot {
  background: var(--color-danger-500);
}

.unread-dot {
  position: absolute;
  left: calc(var(--space-3) - var(--space-2));
  top: 50%;
  transform: translateY(-50%);
  width: var(--size-6);
  height: var(--size-6);
  border-radius: var(--radius-pill);
  background: var(--color-brand-600);
}

.unread-dot.is-hidden {
  visibility: hidden;
}

.execution-main {
  min-width: 0;
  width: 100%;
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

.execution-time {
  color: var(--color-text-muted);
  font-size: var(--text-caption-size);
  white-space: nowrap;
}

.completed-table-wrap {
  border: 0;
  border-radius: 0;
  overflow: visible;
}

.completed-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.completed-col-title {
  width: 34%;
}

.completed-col-agent {
  width: 140px;
}

.completed-col-status {
  width: 84px;
}

.completed-col-time {
  width: auto;
}

.completed-table-row {
  border-bottom: var(--size-1) solid var(--color-border-panel);
}

.completed-table-row:last-child {
  border-bottom: 0;
}

.completed-table-row.is-clickable {
  cursor: pointer;
}

.completed-table-row.is-clickable:hover {
  background: var(--color-bg-soft-hover);
}

.completed-table-row.is-clickable:focus-visible {
  outline: var(--size-2) solid var(--color-brand-500);
  outline-offset: calc(var(--size-2) * -1);
}

.completed-cell {
  padding: var(--space-2_5) var(--space-3);
  vertical-align: middle;
}

.completed-cell-title {
  position: relative;
  padding-left: var(--space-3);
}

.completed-cell-title .execution-title {
  display: block;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.completed-cell-agent {
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
}

:deep(.n-tag.completed-agent-tag) {
  background: color-mix(in srgb, var(--color-text-secondary) 8%, transparent);
  color: var(--color-text-secondary);
  border: var(--size-1) solid color-mix(in srgb, var(--color-text-secondary) 30%, transparent);
}

.completed-cell-status {
}

.completed-cell-time {
  color: var(--color-text-secondary);
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

@media (max-width: 520px) {
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
