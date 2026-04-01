<script setup lang="ts">
import { computed, nextTick } from "vue";
import { NDropdown } from "naive-ui";
import type { DropdownOption } from "naive-ui";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationStore } from "@/stores/conversation";
import { conversationSelectionLabel, formatFriendlyDateTime } from "@/utils/format";

const conversationStore = useConversationStore();
const agentCatalogStore = useAgentCatalogStore();

const description = computed(() => {
  const label = conversationSelectionLabel(
    agentCatalogStore.groups,
    agentCatalogStore.selectedEntryType,
    agentCatalogStore.selectedAgentGroupUid,
    agentCatalogStore.selectedAgentUid
  );
  return agentCatalogStore.selectedEntryType === "group"
    ? `正在查看 ${label} 下的全部对话。`
    : `正在查看 ${label} 下的对话记录。`;
});

function menuOptions(conversationUid: string, title: string): DropdownOption[] {
  return [
    {
      key: "rename",
      label: "修改名称",
      props: {
        onClick: () => {
          const nextTitle = window.prompt("请输入新的对话名称", title || "");
          if (nextTitle && nextTitle.trim()) {
            void conversationStore.renameConversation(conversationUid, nextTitle.trim());
          }
        }
      }
    },
    {
      key: "delete",
      label: "删除",
      props: {
        onClick: () => void conversationStore.confirmDeleteConversation(conversationUid, title)
      }
    }
  ];
}

function createConversationAndFocusInput() {
  conversationStore.startDraftConversation();
  void nextTick(() => {
    const input = document.getElementById("messageInput") as HTMLTextAreaElement | null;
    input?.focus();
  });
}
</script>

<template>
  <aside class="panel conversation-shell">
    <div class="panel-header">
      <div class="panel-title">历史对话</div>
      <div class="panel-subtitle">{{ description }}</div>
    </div>
    <div class="panel-body conversation-panel">
      <button class="create-conversation-button ui-pill-btn" @click="createConversationAndFocusInput()">
        创建新对话
      </button>

      <div class="scroll-area conversation-list">
        <div v-if="conversationStore.filteredConversations.length">
          <div
            v-for="item in conversationStore.filteredConversations"
            :key="item.conversationUid"
            class="conversation-row"
            :class="{ active: conversationStore.currentConversationUid === item.conversationUid }"
          >
            <button class="conversation-main" @click="conversationStore.selectConversation(item.conversationUid)">
              <div class="conversation-title">{{ item.title || "未命名对话" }}</div>
              <div class="conversation-time">更新于 {{ formatFriendlyDateTime(item.updatedTime) }}</div>
            </button>
            <div class="conversation-menu-wrap">
              <n-dropdown trigger="click" :options="menuOptions(item.conversationUid, item.title || '')">
                <button class="history-menu-button">...</button>
              </n-dropdown>
            </div>
          </div>
        </div>
        <div v-else class="conversation-list-empty">暂无历史对话</div>
      </div>
    </div>
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
  gap: var(--space-4);
}

.create-conversation-button {
  width: 100%;
  padding: var(--space-3) var(--space-4_5);
  background: var(--color-bg-brand-soft);
  color: var(--color-accent-brand);
  font-size: var(--size-15);
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.create-conversation-button:hover {
  background: var(--color-bg-brand-soft-hover);
  border-color: var(--color-border-brand-hover);
  box-shadow: var(--shadow-soft-sm);
}

.conversation-list {
  flex: 1;
  width: calc(100% + var(--size-48));
  margin: 0 calc(var(--size-24) * -1);
}

.conversation-list-empty {
  display: grid;
  min-height: 100%;
  place-items: center;
  color: var(--color-text-runtime-subtle);
  font-size: var(--font-size-md);
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
  font-size: var(--font-size-md);
  line-height: 1.4;
  font-weight: 700;
  color: var(--color-text-primary);
}

.conversation-row.active .conversation-title {
  color: var(--color-text-brand);
}

.conversation-time {
  margin-top: var(--space-2);
  font-size: var(--font-size-sm);
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

@media (max-width: var(--size-breakpoint-lg)) {
  .conversation-panel {
    gap: var(--space-3);
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
