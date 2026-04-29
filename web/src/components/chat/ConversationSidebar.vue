<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NDropdown, NInput, NModal, NSelect, type DropdownOption, type InputInst, type SelectOption } from "naive-ui";
import { Pin, RefreshCw } from "lucide-vue-next";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationStore } from "@/stores/conversation";
import { message as discreteMessage } from "@/discrete";
import { formatFriendlyDateTime } from "@/utils/format";
import { getSortLocale } from "@/i18n";

type AgentSelectOption = SelectOption & {
  value: string;
  agentGroupUid: string;
};

const conversationStore = useConversationStore();
const agentCatalogStore = useAgentCatalogStore();
const { t } = useI18n();
const renameDialogVisible = ref(false);
const renameConversationUid = ref("");
const renameInput = ref("");
const renameSubmitting = ref(false);
const renameInputRef = ref<InputInst | null>(null);
const refreshingHistory = ref(false);
const refreshAnimating = ref(false);

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
</script>

<template>
  <aside class="panel conversation-shell">
    <div class="panel-header conversation-header">
      <div class="conversation-header-top">
        <div class="panel-title">{{ t("chat.sidebar.history") }}</div>
        <div class="conversation-header-actions">
          <button
            class="refresh-conversation-button ui-pill-btn"
            :title="t('chat.sidebar.refreshHistoryTooltip')"
            :disabled="refreshingHistory"
            @click="refreshHistoryConversations()"
          >
            <RefreshCw :size="14" :class="{ spinning: refreshAnimating }" />
          </button>
          <button class="create-conversation-button ui-pill-btn" @click="createConversationAndFocusInput()">
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
      <div class="scroll-area conversation-list">
        <div v-if="conversationStore.filteredConversations.length">
          <div
            v-for="item in conversationStore.filteredConversations"
            :key="item.conversationUid"
            class="conversation-row"
            :class="{ active: conversationStore.currentConversationUid === item.conversationUid }"
          >
            <button class="conversation-main" @click="conversationStore.selectConversation(item.conversationUid)">
              <div class="conversation-title">
                <span v-if="item.pinned" class="conversation-pinned-icon" :title="t('chat.sidebar.pinned')">
                  <Pin :size="12" />
                </span>
                <span class="conversation-title-text">{{ item.title || t("chat.sidebar.unnamed") }}</span>
              </div>
              <div class="conversation-time">{{ t("chat.sidebar.updatedAt", { time: formatFriendlyDateTime(item.updatedTime) }) }}</div>
            </button>
            <div class="conversation-menu-wrap">
              <n-dropdown
                trigger="click"
                :options="menuOptions(Boolean(item.pinned))"
                @select="(key) => handleMenuSelect(key, item.conversationUid, item.title || '', Boolean(item.pinned))"
              >
                <button class="history-menu-button">...</button>
              </n-dropdown>
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

.conversation-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 0;
}

.conversation-header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.conversation-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
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
  flex: none;
  white-space: nowrap;
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
  width: calc(100% + var(--size-48));
  margin: 0 calc(var(--space-6) * -1);
}

.conversation-list-empty {
  display: grid;
  min-height: 100%;
  place-items: center;
  color: var(--color-text-runtime-subtle);
  font-size: var(--text-body-size);
}

.conversation-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-1_5);
  padding: var(--space-4) var(--space-3);
  margin: 0;
  border-left: var(--size-3) solid transparent;
  border-radius: 0;
  border-bottom: var(--size-1) solid var(--color-border-panel);
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.conversation-row.active {
  border-left-color: var(--color-accent-brand);
  background: var(--color-bg-surface-soft);
}

.conversation-main {
  flex: 1;
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

.conversation-row.active .conversation-title {
  color: var(--color-text-brand);
}

.conversation-time {
  margin-top: var(--space-2);
  font-size: var(--text-caption-size);
  color: var(--text-muted);
}

.conversation-menu-wrap {
  position: relative;
  display: flex;
  flex-shrink: 0;
  align-self: center;
  margin-right: calc(var(--space-2) * -1);
}

.history-menu-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--size-24);
  height: var(--size-24);
  border: 0;
  background: transparent;
  color: var(--color-text-tertiary);
  font-weight: 700;
  cursor: pointer;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.18s ease, color 0.18s ease;
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

.conversation-row:hover .history-menu-button {
  opacity: 1;
  visibility: visible;
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

@media (max-width: 1120px) {
  .conversation-panel {
    gap: var(--space-3);
  }

  .create-conversation-button {
    padding: var(--space-1) var(--space-2_5);
    font-size: var(--text-caption-size);
  }

  .conversation-list {
    width: 100%;
    margin: 0;
    max-height: var(--size-280);
  }

  .conversation-row {
    padding: var(--space-3_5) var(--space-2_5);
  }
}
</style>
