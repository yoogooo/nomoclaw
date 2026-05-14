<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NCard, NEmpty, NTag } from "naive-ui";
import { useRouter } from "vue-router";
import { useCronJobsStore } from "@/stores/cronJobs";
import { formatDateTime } from "@/utils/format";

const cronJobsStore = useCronJobsStore();
const router = useRouter();
const { t } = useI18n();

const recentResults = computed(() => cronJobsStore.recentGlobalResults);

function executionStatusText(status: string | null | undefined) {
  const normalized = (status || "").toUpperCase();
  if (normalized === "COMPLETED") return t("common.success");
  if (normalized === "FAILED") return t("common.failed");
  if (normalized === "TIMED_OUT_APPROVAL") return t("cron.history.statusTimedOutApproval");
  return normalized || t("common.unknown");
}

function openExecution(result: { executionUid?: string | null; conversationUid?: string | null; messageUid?: string | null }) {
  const executionUid = String(result.executionUid || "").trim();
  const conversationUid = String(result.conversationUid || "").trim();
  const messageUid = String(result.messageUid || "").trim();
  const agentUid = String((result as { agentUid?: string | null }).agentUid || "").trim();
  const jobUid = String((result as { jobUid?: string | null }).jobUid || "").trim();
  if (!executionUid || !conversationUid) {
    return;
  }
  void cronJobsStore.markExecutionRead(executionUid);
  if (jobUid) {
    void cronJobsStore.selectJob(jobUid);
  }
  void router.push({
    path: "/",
    query: {
      source: "cron",
      executionUid,
      conversationUid,
      ...(messageUid ? { messageUid } : {}),
      ...(agentUid ? { agentUid } : {}),
      ...(jobUid ? { jobUid } : {})
    }
  });
}
</script>

<template>
  <div class="panel">
    <div class="panel-header cron-results-header">
      <div class="cron-results-title-wrap">
        <div class="panel-title ui-title-xl">{{ t("cron.detail.tabResult") }}</div>
        <div class="panel-subtitle ui-subtitle">{{ t("cron.detail.globalRecentResults") }}</div>
      </div>
    </div>
    <div class="panel-body scroll-area cron-results-body">
      <n-empty v-if="!recentResults.length" :description="t('cron.detail.noResults')" />
      <div v-else class="recent-result-list">
        <n-card
          v-for="(item, index) in recentResults"
          :key="`${item.executionUid || item.executedTime}-${index}`"
          embedded
          class="recent-result-item"
        >
          <div class="recent-result-head">
            <div class="recent-result-title">{{ item.jobTitle || "未命名任务" }}</div>
            <n-tag size="small" :type="item.status === 'FAILED' || item.status === 'TIMED_OUT_APPROVAL' ? 'error' : 'success'">
              {{ executionStatusText(item.status) }}
            </n-tag>
          </div>
          <div class="recent-result-meta">
            {{ item.agentDisplayName || "" }} · {{ formatDateTime(item.executedTime) }}
          </div>
          <div class="recent-result-summary">{{ item.summary || t("cron.detail.noResultSummary") }}</div>
          <div class="recent-result-actions">
            <n-button text type="primary" :disabled="!item.executionUid" @click="openExecution(item)">查看执行过程</n-button>
          </div>
        </n-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cron-results-header {
  padding: 0;
  border-bottom: 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-3);
}

.cron-results-title-wrap {
  min-width: 0;
}

.recent-result-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.cron-results-body {
  max-height: min(72vh, 860px);
  overflow: auto;
}

.recent-result-item {
  border-radius: var(--radius-lg);
}

.recent-result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
}

.recent-result-title {
  font-size: var(--text-body-size);
  font-weight: 600;
}

.recent-result-meta {
  margin-top: var(--space-2);
  font-size: var(--text-caption-size);
  color: var(--color-text-secondary);
}

.recent-result-summary {
  margin-top: var(--space-2);
  white-space: pre-wrap;
  word-break: break-word;
}

.recent-result-actions {
  margin-top: var(--space-2);
}

@media (max-width: 700px) {
  .cron-results-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
