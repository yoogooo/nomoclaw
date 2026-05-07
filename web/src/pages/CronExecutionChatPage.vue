<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ChevronLeft } from "lucide-vue-next";
import { NButton, NIcon, NSpin } from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import MessagesPanel from "@/components/chat/MessagesPanel.vue";
import { cronApi } from "@/api/cronApi";
import { message } from "@/discrete";
import { useConversationStore } from "@/stores/conversation";
import { useUiPreferencesStore } from "@/stores/uiPreferences";
import type { CronExecutionDetail } from "@/types/api";

const route = useRoute();
const router = useRouter();
const conversationStore = useConversationStore();
const uiPreferencesStore = useUiPreferencesStore();
const loading = ref(false);
const errorText = ref("");
const detail = ref<CronExecutionDetail | null>(null);
const isExecutionRunning = ref(false);
let statusPollTimer: number | null = null;
let openRequestToken = 0;

const executionUid = computed(() => String(route.params.executionUid || "").trim());
const queryConversationUid = computed(() => String(route.query.conversationUid || "").trim());
const queryMessageUid = computed(() => String(route.query.messageUid || "").trim());
const focusMessageUid = computed(() => queryMessageUid.value || String(detail.value?.messageUid || "").trim());
const isLightMode = computed(() => uiPreferencesStore.themeMode === "light");

function isTerminalExecutionStatus(status?: string | null) {
  const normalized = String(status || "").toUpperCase();
  return normalized === "COMPLETED" || normalized === "FAILED" || normalized === "CANCELED";
}

const isAwaitingFinalAssistantReply = computed(() => {
  const targetMessageUid = queryMessageUid.value || String(detail.value?.messageUid || "").trim();
  if (!targetMessageUid) {
    return false;
  }
  const hasFinalAssistantReply = conversationStore.messages.some((item) =>
    item.role === "assistant" && String(item.parentMessageUid || "").trim() === targetMessageUid
  );
  return !hasFinalAssistantReply;
});
const shouldShowProcessingIndicator = computed(() => {
  if (isTerminalExecutionStatus(detail.value?.status)) {
    return false;
  }
  return isAwaitingFinalAssistantReply.value;
});

function clearStatusPollTimer() {
  if (statusPollTimer !== null) {
    window.clearTimeout(statusPollTimer);
    statusPollTimer = null;
  }
}

function syncExecutionRunningStatus(execution?: CronExecutionDetail | null) {
  const status = String(execution?.status || "").toUpperCase();
  isExecutionRunning.value = status === "RUNNING" || status === "IN_PROGRESS";
}

async function refreshExecutionStatus(silent = true) {
  if (!executionUid.value) return;
  try {
    const loaded = await cronApi.getExecutionDetail(executionUid.value, {
      suppressErrorToast: silent
    });
    detail.value = loaded;
    syncExecutionRunningStatus(loaded);
    if (isExecutionRunning.value) {
      clearStatusPollTimer();
      statusPollTimer = window.setTimeout(() => {
        void refreshExecutionStatus(true);
      }, 3000);
    } else {
      clearStatusPollTimer();
    }
  } catch (error) {
    clearStatusPollTimer();
    if (!silent) {
      throw error;
    }
  }
}

async function openExecutionConversation() {
  const token = ++openRequestToken;
  clearStatusPollTimer();
  if (!executionUid.value) {
    errorText.value = "executionUid missing";
    return;
  }
  loading.value = true;
  errorText.value = "";
  try {
    if (!conversationStore.conversations.length) {
      await conversationStore.init();
      if (token !== openRequestToken) return;
    } else {
      await conversationStore.loadModelConfig();
      if (token !== openRequestToken) return;
    }
    // Running executions may not have persisted execution detail yet.
    if (queryConversationUid.value) {
      await conversationStore.selectConversation(queryConversationUid.value);
      if (token !== openRequestToken) return;
      await refreshExecutionStatus(true);
      return;
    }

    const loaded = await cronApi.getExecutionDetail(executionUid.value, {
      suppressErrorToast: queryConversationUid.value.length > 0
    });
    detail.value = loaded;
    syncExecutionRunningStatus(loaded);
    if (isExecutionRunning.value) {
      clearStatusPollTimer();
      statusPollTimer = window.setTimeout(() => {
        void refreshExecutionStatus(true);
      }, 3000);
    }
    if (!loaded.conversationUid) {
      throw new Error("conversation missing");
    }
    await conversationStore.selectConversation(loaded.conversationUid);
    if (token !== openRequestToken) return;
  } catch (error) {
    if (token !== openRequestToken) return;
    const text = error instanceof Error ? error.message : "load failed";
    errorText.value = text;
    const normalized = String(text || "").toLowerCase();
    const shouldFallbackToExecutionDetail = Boolean(executionUid.value)
      && !normalized.includes("execution not found")
      && (
        !detail.value?.conversationUid
        || normalized.includes("resource not found")
        || normalized.includes("conversation")
      );
    if (shouldFallbackToExecutionDetail) {
      message.warning("会话记录不可用，已切换到执行详情页。");
      void router.replace(`/cron/executions/${executionUid.value}`);
    }
  } finally {
    if (token === openRequestToken) {
      loading.value = false;
    }
  }
}

watch(
  () => [executionUid.value, queryConversationUid.value, String(route.query.messageUid || "").trim()],
  () => {
    void openExecutionConversation();
  },
  { immediate: true }
);

watch(
  () => executionUid.value,
  (value) => {
    conversationStore.setSkipConversationListRefresh(Boolean(value));
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  clearStatusPollTimer();
  conversationStore.setSkipConversationListRefresh(false);
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="cron-execution-chat-content" :class="{ 'is-light': isLightMode }">
          <header class="cron-execution-chat-header">
            <button class="cron-execution-chat-back" type="button" @click="router.push('/cron')">
              <n-icon :component="ChevronLeft" />
              <span>返回任务页</span>
            </button>
          </header>
          <n-spin :show="loading">
            <section v-if="errorText" class="panel">
              <div class="panel-header">
                <div class="panel-title ui-title-lg">执行会话不可用</div>
              </div>
              <div class="panel-body cron-execution-chat-error">
                <div>{{ errorText }}</div>
                <n-button type="primary" @click="router.push('/cron')">返回任务页</n-button>
              </div>
            </section>
            <MessagesPanel
              v-else
              :key="`${executionUid}:${queryConversationUid}:${focusMessageUid}`"
              :hide-composer="true"
              :focus-message-uid="focusMessageUid"
              :force-typing-indicator="shouldShowProcessingIndicator"
              :hide-typing-role-label="true"
              :disable-auto-typing-indicator="true"
            />
          </n-spin>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.cron-execution-chat-content {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-2);
}

.cron-execution-chat-header {
  display: flex;
  align-items: center;
}

.cron-execution-chat-back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1_5);
  padding: var(--space-1) var(--space-1_5);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
  cursor: pointer;
  transition: color 0.16s ease, background-color 0.16s ease;
}

.cron-execution-chat-back:hover {
  color: var(--color-text-heading);
  background: var(--color-bg-soft-hover);
}

.cron-execution-chat-content :deep(.message-panel) {
  height: 100%;
  min-width: 0;
  max-width: 100%;
}

.cron-execution-chat-content :deep(.message-list) {
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
}

.cron-execution-chat-content :deep(.message-wrap),
.cron-execution-chat-content :deep(.run-card),
.cron-execution-chat-content :deep(.ui-approval-card) {
  max-width: min(100%, var(--container-xl));
}

.cron-execution-chat-content.is-light :deep(.message-bubble:not(.user)) {
  background: var(--color-bg-surface);
  border: var(--size-1) solid var(--color-border-soft);
}

.cron-execution-chat-content :deep(.ui-approval-body pre) {
  max-width: 100%;
  overflow-x: hidden;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.cron-execution-chat-content :deep(.ui-approval-body pre code) {
  display: block;
  width: 100%;
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.cron-execution-chat-error {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
</style>
