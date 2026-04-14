<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NCard, NDropdown, NInput, NModal } from "naive-ui";
import type { DropdownOption, InputInst } from "naive-ui";
import { RefreshCw } from "lucide-vue-next";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationStore } from "@/stores/conversation";
import { message as discreteMessage } from "@/discrete";
import { conversationSelectionLabel, formatFriendlyDateTime } from "@/utils/format";

const conversationStore = useConversationStore();
const agentCatalogStore = useAgentCatalogStore();
const { t, locale } = useI18n();
const renameDialogVisible = ref(false);
const renameConversationUid = ref("");
const renameInput = ref("");
const renameSubmitting = ref(false);
const renameInputRef = ref<InputInst | null>(null);
const refreshingHistory = ref(false);

function hasHanText(value: string) {
  return /[\u4e00-\u9fff]/.test(value);
}

function normalizeLabelForLocale(label: string) {
  if (locale.value !== "en-US" || !hasHanText(label)) {
    return label;
  }
  if (agentCatalogStore.selectedEntryType === "group") {
    return agentCatalogStore.selectedAgentGroupUid || label;
  }
  const selectedAgent = agentCatalogStore.allAgents.find((item) => item.agentUid === agentCatalogStore.selectedAgentUid);
  return selectedAgent?.agentName || label;
}

const description = computed(() => {
  const rawLabel = conversationSelectionLabel(
    agentCatalogStore.groups,
    agentCatalogStore.selectedEntryType,
    agentCatalogStore.selectedAgentGroupUid,
    agentCatalogStore.selectedAgentUid
  );
  const label = normalizeLabelForLocale(rawLabel);
  return agentCatalogStore.selectedEntryType === "group"
    ? t("chat.sidebar.viewingGroup", { label })
    : t("chat.sidebar.viewingAgent", { label });
});

type ConversationMenuKey = "rename" | "delete";

function menuOptions(): DropdownOption[] {
  return [
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

function handleMenuSelect(key: string | number, conversationUid: string, title: string) {
  const action = String(key) as ConversationMenuKey;
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
  refreshingHistory.value = true;
  try {
    await conversationStore.refreshConversations();
  } catch {
    discreteMessage.error(t("toast.refreshFailed"));
  } finally {
    refreshingHistory.value = false;
  }
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
            <RefreshCw :size="14" :class="{ spinning: refreshingHistory }" />
          </button>
          <button class="create-conversation-button ui-pill-btn" @click="createConversationAndFocusInput()">
            {{ t("chat.sidebar.createConversation") }}
          </button>
        </div>
      </div>
      <div class="panel-subtitle">{{ description }}</div>
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
              <div class="conversation-title">{{ item.title || t("chat.sidebar.unnamed") }}</div>
              <div class="conversation-time">{{ t("chat.sidebar.updatedAt", { time: formatFriendlyDateTime(item.updatedTime) }) }}</div>
            </button>
            <div class="conversation-menu-wrap">
              <n-dropdown
                trigger="click"
                :options="menuOptions()"
                @select="(key) => handleMenuSelect(key, item.conversationUid, item.title || '')"
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

.refresh-conversation-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--size-32);
  height: var(--size-32);
  padding: 0;
  border-color: var(--color-border-strong);
  background: var(--color-bg-surface-soft);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease;
}

.refresh-conversation-button:hover:not(:disabled) {
  border-color: var(--color-border-active);
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
  gap: var(--space-2);
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-body-size);
  line-height: 1.4;
  font-weight: 500;
  color: var(--color-text-primary);
}

.conversation-row.active .conversation-title {
  color: var(--color-text-brand);
}

.conversation-time {
  margin-top: var(--space-2);
  font-size: var(--text-body-size);
  color: var(--text-muted);
}

.conversation-menu-wrap {
  position: relative;
  display: flex;
  flex-shrink: 0;
  align-self: center;
}

.history-menu-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--size-32);
  height: var(--size-32);
  border: 0;
  background: transparent;
  color: var(--color-text-tertiary);
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
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: var(--size-breakpoint-lg)) {
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
