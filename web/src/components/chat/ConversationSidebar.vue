<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NDropdown, NInput, NModal, NSelect, type DropdownOption, type InputInst, type SelectOption } from "naive-ui";
import { Clock3, LoaderCircle, MoreHorizontal, Pin, RefreshCw } from "lucide-vue-next";
import { useRoute } from "vue-router";
import { cronApi } from "@/api/cronApi";
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
const conversationListRef = ref<HTMLElement | null>(null);
const lastAutoScrolledConversationUid = ref("");

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
    await conversationStore.refreshConversations();
    await refreshCronConversationBindings();
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
    cronTaskByConversationUid.value = nextMap;
  } catch {
    // best effort
  }
}

void refreshCronConversationBindings();

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

watch(
  () => [conversationStore.currentConversationUid, conversationStore.filteredConversations.length, String(route.query.source || "")],
  () => {
    void scrollActiveConversationIntoViewIfNeeded();
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

function isConversationRunning(conversationUid: string) {
  const normalizedConversationUid = String(conversationUid || "").trim();
  const normalizedRunningUid = String(conversationStore.runningConversationUid || "").trim();
  return Boolean(normalizedConversationUid && normalizedRunningUid && normalizedConversationUid === normalizedRunningUid);
}

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
        <div class="conversation-header-actions">
          <button class="create-conversation-button ui-control-btn" @click="createConversationAndFocusInput()">
            {{ t("chat.sidebar.createConversation") }}
          </button>
        </div>
      </div>
      <div class="agent-select-wrap">
        <div class="agent-select-label">{{ t("chat.sidebar.agentSelector") }}</div>
        <n-select
          :value="selectedAgentUid"
          :options="agentOptions"
          filterable
          size="small"
          :placeholder="t('chat.sidebar.agentSelectorPlaceholder')"
          @update:value="handleAgentChange"
        />
      </div>
    </div>
    <div class="panel-body conversation-panel">
      <div ref="conversationListRef" class="scroll-area conversation-list">
        <div v-if="conversationStore.filteredConversations.length">
          <div
            v-for="item in conversationStore.filteredConversations"
            :key="item.conversationUid"
            class="conversation-row"
            :class="{ active: conversationStore.currentConversationUid === item.conversationUid }"
          >
            <button class="conversation-main" @click="conversationStore.selectConversation(item.conversationUid)">
              <div class="conversation-title">
                <span v-if="item.unread" class="conversation-unread-dot" :title="t('chat.sidebar.unreadHint')" aria-label="unread" />
                <span v-if="item.pinned" class="conversation-pinned-icon" :title="t('chat.sidebar.pinned')">
                  <Pin :size="12" />
                </span>
                <span
                  v-if="isConversationRunning(item.conversationUid)"
                  class="conversation-running-icon"
                  :title="t('cron.execution.statusRunning')"
                  aria-label="running"
                >
                  <LoaderCircle :size="12" />
                </span>
                <span v-if="isCronConversation(item.conversationUid)" class="conversation-cron-icon" aria-hidden="true">
                  <Clock3 :size="12" />
                </span>
                <span class="conversation-title-text">{{ item.title || t("chat.sidebar.unnamed") }}</span>
              </div>
            </button>
            <div class="conversation-meta-slot">
              <div v-if="item.waitingApproval" class="conversation-status-pill waiting-approval-pill">
                {{ t("chat.sidebar.waitingApproval") }}
              </div>
              <div v-else class="conversation-time-inline" :title="formatDateTime(item.updatedTime)">
                {{ formatConversationListTime(item.updatedTime, locale) }}
              </div>
              <div class="conversation-menu-wrap">
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

.conversation-header-top {
  display: flex;
  align-items: flex-start;
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

.conversation-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  justify-content: flex-start;
  min-width: 0;
}

.agent-select-wrap {
  margin-top: var(--space-3_5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.agent-select-label {
  font-size: var(--text-caption-size);
  color: var(--color-text-secondary);
  font-weight: 600;
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
  white-space: nowrap;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: var(--space-1_5) var(--space-3);
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

.conversation-main {
  width: 100%;
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
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

.conversation-running-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-success-500);
  flex: none;
}

.conversation-running-icon :deep(svg) {
  transform-origin: center;
  animation: conversation-running-spin 0.9s linear infinite;
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
