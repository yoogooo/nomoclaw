<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NDropdown, NInput, NModal, NSelect, type DropdownOption, type InputInst, type SelectOption } from "naive-ui";
import { Clock3, MoreHorizontal, Pin, RefreshCw } from "lucide-vue-next";
import { useRoute } from "vue-router";
import { cronApi } from "@/api/cronApi";
import UiSpinner from "@/components/UiSpinner.vue";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationStore } from "@/stores/conversation";
import { message as discreteMessage } from "@/discrete";
import { formatConversationListTime, formatDateTime } from "@/utils/format";
import { getSortLocale } from "@/i18n";

type AgentSelectOption = SelectOption & {
  value: string;
  agentGroupUid: string;
};

const conversationStore = useConversationStore();
const agentCatalogStore = useAgentCatalogStore();
const route = useRoute();
const { t, locale } = useI18n();
const renameDialogVisible = ref(false);
const renameConversationUid = ref("");
const renameInput = ref("");
const renameSubmitting = ref(false);
const renameInputRef = ref<InputInst | null>(null);
const refreshingHistory = ref(false);
const refreshAnimating = ref(false);
const cronTaskByConversationUid = ref<Record<string, string>>({});
const runningConversationUids = ref<Record<string, true>>({});
const conversationListRef = ref<HTMLElement | null>(null);
const lastAutoScrolledConversationUid = ref("");
let cronBindingsPollTimer: number | null = null;

const agentOptions = computed<AgentSelectOption[]>(() =>
  [...agentCatalogStore.allAgents]
    .sort((left, right) => {
      if (left.sortIndex !== right.sortIndex) {
        return left.sortIndex - right.sortIndex;
      }
      return (left.displayName || left.agentName).localeCompare(right.displayName || right.agentName, getSortLocale());
    })
    .map((agent) => ({
      label: agent.displayName || agent.agentName,
      value: agent.agentUid,
      agentGroupUid: agent.agentGroupUid
    }))
);

const selectedAgentUid = computed(() => agentCatalogStore.selectedAgentUid);

type ConversationMenuKey = "rename" | "delete" | "togglePin";

function menuOptions(pinned: boolean): DropdownOption[] {
  return [
    {
      key: "togglePin",
      label: pinned ? t("chat.sidebar.unpin") : t("chat.sidebar.pin")
    },
    {
      key: "rename",
      label: t("chat.sidebar.rename")
    },
    {
      key: "delete",
      label: t("chat.sidebar.delete")
    }
  ];
}

function openRenameDialog(conversationUid: string, title: string) {
  renameConversationUid.value = conversationUid;
  renameInput.value = title || "";
  renameDialogVisible.value = true;
  void nextTick(() => renameInputRef.value?.focus());
}

function closeRenameDialog() {
  renameDialogVisible.value = false;
  renameConversationUid.value = "";
  renameInput.value = "";
  renameSubmitting.value = false;
}

async function confirmRenameConversation() {
  const normalizedTitle = renameInput.value.trim();
  if (!normalizedTitle) {
    discreteMessage.warning(t("chat.sidebar.renamePrompt"));
    return;
  }
  if (!renameConversationUid.value) {
    closeRenameDialog();
    return;
  }
  renameSubmitting.value = true;
  try {
    await conversationStore.renameConversation(renameConversationUid.value, normalizedTitle);
    closeRenameDialog();
  } finally {
    renameSubmitting.value = false;
  }
}

function handleMenuSelect(key: string | number, conversationUid: string, title: string, pinned: boolean) {
  const action = String(key) as ConversationMenuKey;
  if (action === "togglePin") {
    void conversationStore.updateConversationPin(conversationUid, !pinned);
    return;
  }
  if (action === "rename") {
    openRenameDialog(conversationUid, title);
    return;
  }
  if (action === "delete") {
    void conversationStore.confirmDeleteConversation(conversationUid, title);
  }
}

function createConversationAndFocusInput() {
  conversationStore.startDraftConversation();
  void nextTick(() => {
    const input = document.getElementById("messageInput") as HTMLTextAreaElement | null;
    input?.focus();
  });
}

async function refreshHistoryConversations() {
  if (refreshingHistory.value) {
    return;
  }
  const startedAt = Date.now();
  refreshingHistory.value = true;
  refreshAnimating.value = true;
  try {
    await conversationStore.refreshConversations(conversationStore.currentConversationUid, true, true);
    await refreshCronConversationBindings();
    await fillConversationViewportIfNeeded();
  } catch {
    discreteMessage.error(t("toast.refreshFailed"));
  } finally {
    refreshingHistory.value = false;
    const elapsed = Date.now() - startedAt;
    window.setTimeout(() => {
      refreshAnimating.value = false;
    }, Math.max(0, 650 - elapsed));
  }
}

async function refreshCronConversationBindings() {
  try {
    const [recentResults, runningResults] = await Promise.all([
      cronApi.listGlobalRecentResults(200),
      cronApi.listGlobalRunningResults(200)
    ]);
    const nextMap: Record<string, string> = {};
    const nextRunning: Record<string, true> = {};
    for (const item of runningResults) {
      const conversationUid = String(item.conversationUid || "").trim();
      if (!conversationUid) {
        continue;
      }
      nextRunning[conversationUid] = true;
    }
    for (const item of [...runningResults, ...recentResults]) {
      const conversationUid = String(item.conversationUid || "").trim();
      if (!conversationUid || nextMap[conversationUid]) {
        continue;
      }
      const title = String(item.jobTitle || "").trim();
      if (title) {
        nextMap[conversationUid] = title;
      }
    }
    if (!areStringRecordMapsEqual(cronTaskByConversationUid.value, nextMap)) {
      cronTaskByConversationUid.value = nextMap;
    }
    if (!areStringRecordMapsEqual(runningConversationUids.value, nextRunning)) {
      runningConversationUids.value = nextRunning;
    }
  } catch {
    // best effort
  }
}

function areStringRecordMapsEqual(
  current: Record<string, string | true>,
  next: Record<string, string | true>
) {
  const currentKeys = Object.keys(current);
  const nextKeys = Object.keys(next);
  if (currentKeys.length !== nextKeys.length) {
    return false;
  }
  return currentKeys.every((key) => current[key] === next[key]);
}

function stopCronBindingsPolling() {
  if (cronBindingsPollTimer !== null) {
    window.clearTimeout(cronBindingsPollTimer);
    cronBindingsPollTimer = null;
  }
}

function scheduleCronBindingsPolling() {
  stopCronBindingsPolling();
  if (typeof window === "undefined" || !Object.keys(runningConversationUids.value).length) {
    return;
  }
  cronBindingsPollTimer = window.setTimeout(async () => {
    await refreshCronConversationBindings();
    scheduleCronBindingsPolling();
  }, 3000);
}

void refreshCronConversationBindings();

watch(
  () => Object.keys(runningConversationUids.value).sort().join(","),
  () => {
    scheduleCronBindingsPolling();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  stopCronBindingsPolling();
});

watch(
  () => [
    String(route.query.source || "").trim().toLowerCase(),
    String(route.query.conversationUid || "").trim(),
    String(route.query.executionUid || "").trim(),
    String(route.query.jobUid || "").trim()
  ],
  async ([source, conversationUid, executionUid, jobUid]) => {
    if (source !== "cron" || !conversationUid) return;
    if (cronTaskByConversationUid.value[conversationUid]) return;

    let title = "";
    if (executionUid) {
      try {
        const detail = await cronApi.getExecutionDetail(executionUid, { suppressErrorToast: true });
        title = String(detail?.jobTitle || "").trim();
      } catch {
        // best effort
      }
    }
    if (!title && jobUid) {
      try {
        const jobs = await cronApi.listCronJobs();
        const matched = jobs.find((item) => String(item.jobUid || "").trim() === jobUid);
        title = String(matched?.title || matched?.taskContent || "").trim();
      } catch {
        // best effort
      }
    }
    if (title) {
      cronTaskByConversationUid.value = {
        ...cronTaskByConversationUid.value,
        [conversationUid]: title
      };
    }
  },
  { immediate: true }
);

async function scrollActiveConversationIntoViewIfNeeded() {
  const source = String(route.query.source || "").trim().toLowerCase();
  if (source !== "cron") return;
  const conversationUid = String(conversationStore.currentConversationUid || "").trim();
  if (!conversationUid) return;
  if (lastAutoScrolledConversationUid.value === conversationUid) return;
  await nextTick();
  const container = conversationListRef.value;
  if (!container) return;
  const activeRow = container.querySelector(".conversation-row.active") as HTMLElement | null;
  if (!activeRow) return;
  activeRow.scrollIntoView({ block: "center", inline: "nearest", behavior: "auto" });
  lastAutoScrolledConversationUid.value = conversationUid;
}

async function fillConversationViewportIfNeeded() {
  await nextTick();
  const element = conversationListRef.value;
  if (!element) {
    return;
  }
  let guard = 0;
  while (
    conversationStore.conversationListHasMore
    && !conversationStore.conversationListLoading
    && element.scrollHeight <= element.clientHeight
    && guard < 20
  ) {
    guard += 1;
    await conversationStore.loadMoreConversations();
    await nextTick();
  }
}

watch(
  () => [conversationStore.currentConversationUid, conversationStore.filteredConversations.length, String(route.query.source || "")],
  () => {
    void scrollActiveConversationIntoViewIfNeeded();
    void fillConversationViewportIfNeeded();
  },
  { immediate: true }
);

function handleAgentChange(agentUid: string | number | null) {
  if (!agentUid) {
    return;
  }
  const selected = agentOptions.value.find((item) => item.value === String(agentUid));
  if (!selected) {
    return;
  }
  agentCatalogStore.selectAgent(selected.agentGroupUid, selected.value);
  void conversationStore.applyAgentSelection();
}

function isCronConversation(conversationUid: string) {
  return Boolean(cronTaskByConversationUid.value[String(conversationUid || "").trim()]);
}

function isConversationRunning(conversationUid: string, running?: boolean) {
  if (running) {
    return true;
  }
  const normalizedConversationUid = String(conversationUid || "").trim();
  if (!normalizedConversationUid) {
    return false;
  }
  if (runningConversationUids.value[normalizedConversationUid]) {
    return true;
  }
  return Boolean(conversationStore.runningConversationUids[normalizedConversationUid]);
}

function handleConversationListScroll(event: Event) {
  const element = event.target as HTMLElement | null;
  if (!element) {
    return;
  }
  const remaining = element.scrollHeight - element.scrollTop - element.clientHeight;
  if (remaining <= 120) {
    void conversationStore.loadMoreConversations();
  }
}

function handleWindowResize() {
  void fillConversationViewportIfNeeded();
}

onMounted(() => {
  window.addEventListener("resize", handleWindowResize);
  void fillConversationViewportIfNeeded();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleWindowResize);
});

</script>

<template>
  <aside class="panel conversation-shell">
    <div class="panel-header conversation-header">
      <div class="conversation-header-top">
        <div class="conversation-header-title-row">
          <div class="panel-title">{{ t("chat.sidebar.history") }}</div>
          <button
            class="refresh-conversation-button ui-pill-btn"
            :title="t('chat.sidebar.refreshHistoryTooltip')"
            :disabled="refreshingHistory"
            @click="refreshHistoryConversations()"
          >
            <RefreshCw :size="14" :class="{ spinning: refreshAnimating }" />
          </button>
        </div>
        <div class="conversation-header-toolbar">
          <div class="agent-select-wrap">
            <n-select
              :value="selectedAgentUid"
              :options="agentOptions"
              filterable
              size="small"
              :placeholder="t('chat.sidebar.agentSelectorPlaceholder')"
              @update:value="handleAgentChange"
            />
          </div>
          <div class="conversation-header-actions">
            <button class="create-conversation-button ui-control-btn" @click="createConversationAndFocusInput()">
              {{ t("chat.sidebar.createConversation") }}
            </button>
          </div>
        </div>
      </div>
    </div>
    <div class="panel-body conversation-panel">
      <div
        ref="conversationListRef"
        class="scroll-area conversation-list"
        @scroll.passive="handleConversationListScroll"
      >
        <div v-if="conversationStore.filteredConversations.length">
          <div
            v-for="item in conversationStore.filteredConversations"
            :key="item.conversationUid"
            class="conversation-row"
            :class="{ active: conversationStore.currentConversationUid === item.conversationUid }"
            role="button"
            tabindex="0"
            @click="conversationStore.selectConversation(item.conversationUid)"
            @keydown.enter.prevent="conversationStore.selectConversation(item.conversationUid)"
            @keydown.space.prevent="conversationStore.selectConversation(item.conversationUid)"
          >
            <div class="conversation-main">
              <div class="conversation-title">
                <span v-if="item.pinned" class="conversation-pinned-icon" :title="t('chat.sidebar.pinned')">
                  <Pin :size="12" />
                </span>
                <span v-if="isCronConversation(item.conversationUid)" class="conversation-cron-icon" aria-hidden="true">
                  <Clock3 :size="12" />
                </span>
                <span class="conversation-title-text">{{ item.title || t("chat.sidebar.unnamed") }}</span>
              </div>
            </div>
            <div class="conversation-meta-slot">
              <div v-if="item.waitingApproval" class="conversation-status-pill waiting-approval-pill">
                {{ t("chat.sidebar.waitingApproval") }}
              </div>
              <div
                v-else-if="isConversationRunning(item.conversationUid, item.running)"
                class="conversation-running-meta-icon"
                :title="t('cron.execution.statusRunning')"
                aria-label="running"
              >
                <UiSpinner class="conversation-running-spinner" :size="14" thickness="1.8px" />
              </div>
              <div
                v-else-if="item.unread"
                class="conversation-unread-meta-icon"
                :title="t('chat.sidebar.unreadHint')"
                aria-label="unread"
              >
                <span class="conversation-unread-dot" />
              </div>
              <div v-else class="conversation-time-inline" :title="formatDateTime(item.lastUserMessageTime)">
                {{ formatConversationListTime(item.lastUserMessageTime, locale) }}
              </div>
              <div class="conversation-menu-wrap" @click.stop>
                <n-dropdown
                  trigger="click"
                  :options="menuOptions(Boolean(item.pinned))"
                  @select="(key) => handleMenuSelect(key, item.conversationUid, item.title || '', Boolean(item.pinned))"
                >
                  <button class="history-menu-button" aria-label="actions">
                    <MoreHorizontal :size="16" />
                  </button>
                </n-dropdown>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="conversation-list-empty">{{ t("chat.sidebar.noConversations") }}</div>
        <div v-if="conversationStore.conversationListLoading" class="conversation-list-loading">
          {{ t("chat.sidebar.loadingMore") }}
        </div>
        <div
          v-else-if="conversationStore.conversationListHasMore"
          class="conversation-list-load-more-wrap"
        >
          <button
            type="button"
            class="conversation-list-load-more-btn"
            @click="conversationStore.loadMoreConversations()"
          >
            {{ t("chat.sidebar.loadMore") }}
          </button>
        </div>
      </div>
    </div>
    <n-modal v-model:show="renameDialogVisible" preset="card" :title="t('chat.sidebar.rename')" style="width: min(520px, 92vw)">
      <div class="rename-dialog-body">
        <n-input
          ref="renameInputRef"
          v-model:value="renameInput"
          :placeholder="t('chat.sidebar.renamePrompt')"
          maxlength="120"
          @keydown.enter.prevent="confirmRenameConversation"
        />
      </div>
      <template #footer>
        <div class="rename-dialog-actions">
          <n-button quaternary :disabled="renameSubmitting" @click="closeRenameDialog">{{ t("common.cancel") }}</n-button>
          <n-button type="primary" :loading="renameSubmitting" @click="confirmRenameConversation">{{ t("common.confirm") }}</n-button>
        </div>
      </template>
    </n-modal>
  </aside>
</template>

<style scoped>
.conversation-shell {
  min-height: 0;
}

.conversation-header {
  padding-top: var(--space-2);
  padding-right: var(--space-4) !important;
  padding-bottom: 0;
  padding-left: var(--space-4) !important;
}

.conversation-shell .conversation-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 0;
  padding-top: var(--space-5);
  padding-right: 0 !important;
  padding-bottom: 0;
  padding-left: 0 !important;
}

.conversation-update-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin: 0 var(--space-4) var(--space-3);
  padding: var(--space-2_5) var(--space-3);
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--color-warning-soft) 78%, white);
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
}

.conversation-update-banner-btn {
  border: none;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  font: inherit;
  font-weight: 600;
}

.conversation-list-loading {
  padding: var(--space-3) var(--space-4);
  color: var(--color-text-muted);
  font-size: var(--text-caption-size);
  text-align: center;
}

.conversation-list-load-more-wrap {
  display: flex;
  justify-content: center;
  padding: var(--space-2) var(--space-4) var(--space-4);
}

.conversation-list-load-more-btn {
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-pill);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  padding: var(--space-2) var(--space-4);
  font: inherit;
  cursor: pointer;
  transition: border-color 0.16s ease, color 0.16s ease, background-color 0.16s ease;
}

.conversation-list-load-more-btn:hover {
  border-color: var(--color-border-active);
  color: var(--color-text-primary);
  background: var(--color-bg-soft-hover);
}

.conversation-header-top {
  display: flex;
  align-items: stretch;
  justify-content: flex-start;
  flex-direction: column;
  gap: var(--space-3);
}

.conversation-header-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: var(--space-2);
}

.conversation-header-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
}

.conversation-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: auto;
  justify-content: flex-end;
  min-width: 0;
}

.agent-select-wrap {
  min-width: 0;
}

.refresh-conversation-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--size-32);
  height: var(--size-32);
  padding: 0;
  border-color: transparent;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease;
}

.refresh-conversation-button:hover:not(:disabled) {
  border-color: transparent;
  background: var(--color-bg-soft-hover);
  color: var(--color-text-primary);
}

.refresh-conversation-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.create-conversation-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  height: 28px;
  white-space: nowrap;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 var(--space-3);
  border-color: var(--color-border-strong);
  background: var(--color-bg-surface-soft);
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease;
}

.create-conversation-button:hover {
  border-color: var(--color-border-active);
  background: var(--color-bg-soft-hover);
  color: var(--color-text-primary);
}

@media (max-width: 900px) {
  .conversation-header-toolbar {
    grid-template-columns: 1fr;
  }

  .conversation-header-actions {
    width: 100%;
  }

  .create-conversation-button {
    width: 100%;
  }
}

.conversation-list {
  flex: 1;
  width: 100%;
  margin: 0;
}

.conversation-list-empty {
  display: grid;
  min-height: 100%;
  place-items: center;
  color: var(--color-text-runtime-subtle);
  font-size: var(--text-body-size);
}

.conversation-row {
  --conversation-meta-min-width: 3.5rem;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  column-gap: var(--space-1_5);
  padding-top: var(--space-2_5);
  padding-right: var(--space-2);
  padding-bottom: var(--space-2_5);
  padding-left: var(--space-2);
  margin: 0;
  border-left: var(--size-3) solid transparent;
  border-radius: 0;
  border-bottom: var(--size-1) solid color-mix(in srgb, var(--color-border-panel) 58%, transparent);
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.conversation-row:hover:not(.active) {
  background: color-mix(in srgb, var(--color-bg-surface-soft) 82%, var(--color-bg-soft-hover) 18%);
  border-left-color: color-mix(in srgb, var(--color-border-active) 36%, transparent);
}

.conversation-row.active {
  border-left-color: var(--color-accent-brand);
  background: var(--color-bg-surface-soft);
}

.conversation-row:focus-visible {
  outline: var(--size-2) solid color-mix(in srgb, var(--color-border-active) 72%, white 28%);
  outline-offset: calc(var(--size-1) * -1);
}

.conversation-main {
  width: 100%;
  min-width: 0;
  color: var(--color-text-primary);
  text-align: left;
  transition: color 0.18s ease;
}

.conversation-row.active .conversation-main {
  color: var(--color-text-brand);
}

.conversation-row:hover:not(.active) .conversation-main {
  color: var(--color-text-heading);
}

.conversation-row:hover:not(.active) .conversation-time-inline {
  color: var(--color-text-secondary);
}

.conversation-title {
  display: flex;
  align-items: center;
  gap: var(--space-1_5);
  overflow: hidden;
  font-size: var(--text-body-size);
  line-height: 1.4;
  font-weight: 400;
  color: var(--color-text-primary);
}

.conversation-title-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-pinned-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  flex: none;
}

.conversation-running-meta-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--size-24);
  height: var(--size-24);
  margin-right: 2px;
  transition: opacity 0.18s ease, visibility 0.18s ease;
}

.conversation-running-spinner {
  color: #0f8f5c;
}

:root[data-theme="dark"] .conversation-running-spinner {
  color: #3dffb5;
}

.conversation-unread-meta-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--size-24);
  height: var(--size-24);
  margin-right: 2px;
  transition: opacity 0.18s ease, visibility 0.18s ease;
}

.conversation-row.active .conversation-title {
  color: var(--color-text-brand);
}

.conversation-unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e5484d;
  flex: none;
}

.conversation-cron-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  flex: none;
}

.conversation-time-inline {
  min-width: 0;
  text-align: right;
  font-size: 10px;
  color: var(--color-text-tertiary);
  white-space: nowrap;
  transition: opacity 0.18s ease, visibility 0.18s ease;
}

.conversation-status-pill {
  min-width: 0;
  max-width: 100%;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 9px;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 2px;
}

.waiting-approval-pill {
  color: #8a4b00;
  background: #ffe5bf;
  border: 1px solid #ffcc80;
  margin-right: -8px;
}

.conversation-meta-slot {
  position: relative;
  min-width: var(--conversation-meta-min-width);
  width: max-content;
  height: var(--size-24);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 6px;
}

.conversation-menu-wrap {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 0.18s ease, visibility 0.18s ease;
  padding-right: 6px;
}

.conversation-menu-wrap :deep(.n-dropdown-trigger) {
  display: flex;
  width: var(--size-24);
  height: var(--size-24);
  align-items: center;
  justify-content: center;
}

.history-menu-button {
  display: grid;
  place-items: center;
  width: var(--size-24);
  height: var(--size-24);
  padding: 0;
  line-height: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: color 0.18s ease;
}

.rename-dialog-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-top: var(--space-1);
}

.rename-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.conversation-row:hover .conversation-menu-wrap {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
}

.conversation-row:hover .conversation-time-inline {
  opacity: 0;
  visibility: hidden;
}

.conversation-row:hover .conversation-status-pill {
  opacity: 0;
  visibility: hidden;
}

.conversation-row:hover .conversation-running-meta-icon {
  opacity: 0;
  visibility: hidden;
}

.conversation-row:hover .conversation-unread-meta-icon {
  opacity: 0;
  visibility: hidden;
}

.history-menu-button:hover {
  color: var(--color-text-slate-700);
}

.conversation-row.active .history-menu-button {
  color: var(--color-text-secondary);
}

.spinning {
  transform-origin: center;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes conversation-running-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1120px) {
  .conversation-header {
    padding-top: var(--space-1_5);
    padding-right: var(--space-3) !important;
    padding-left: var(--space-3) !important;
  }

  .conversation-header-actions {
    justify-content: flex-start;
  }

  .conversation-shell .conversation-panel {
    gap: var(--space-3);
    padding-top: var(--space-3_5);
    padding-right: 0 !important;
    padding-left: 0 !important;
  }

  .create-conversation-button {
    padding: var(--space-1) var(--space-2_5);
    font-size: var(--text-caption-size);
  }

  .conversation-list {
    width: 100%;
    margin: 0;
  }

  .conversation-row {
    padding: var(--space-2) var(--space-1_5);
  }
}
</style>
