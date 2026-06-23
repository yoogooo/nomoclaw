<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { MessageCircle, Search, X } from "lucide-vue-next";
import { NButton, NInput, NModal, type InputInst } from "naive-ui";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationStore } from "@/stores/conversation";
import { formatFriendlyDateTime } from "@/utils/format";

const conversationStore = useConversationStore();
const agentCatalogStore = useAgentCatalogStore();
const { t } = useI18n();
const inputRef = ref<InputInst | null>(null);
let searchTimer: number | null = null;

const selectedResult = computed(() => conversationStore.searchResults[conversationStore.searchSelectedIndex] || null);

function clearSearchTimer() {
  if (searchTimer !== null) {
    window.clearTimeout(searchTimer);
    searchTimer = null;
  }
}

function resetSearchResults() {
  conversationStore.searchResults = [];
  conversationStore.searchCursor = null;
  conversationStore.searchHasMore = false;
  conversationStore.searchLoading = false;
  conversationStore.searchSelectedIndex = 0;
}

function scheduleSearch() {
  clearSearchTimer();
  searchTimer = window.setTimeout(() => {
    void conversationStore.searchConversationHistory(true);
  }, 280);
}

function handleDialogVisibleChange(show: boolean) {
  if (show) {
    conversationStore.openConversationSearch();
    return;
  }
  conversationStore.closeConversationSearch();
}

async function handleOpenSelectedResult() {
  if (!selectedResult.value) {
    return;
  }
  await conversationStore.openConversationSearchResult(selectedResult.value);
}

function moveSelection(offset: number) {
  const nextIndex = conversationStore.searchSelectedIndex + offset;
  const maxIndex = conversationStore.searchResults.length - 1;
  if (maxIndex < 0) {
    conversationStore.searchSelectedIndex = 0;
    return;
  }
  conversationStore.searchSelectedIndex = Math.max(0, Math.min(maxIndex, nextIndex));
}

function handleInputKeydown(event: KeyboardEvent) {
  if (event.key === "ArrowDown") {
    event.preventDefault();
    moveSelection(1);
    return;
  }
  if (event.key === "ArrowUp") {
    event.preventDefault();
    moveSelection(-1);
    return;
  }
  if (event.key === "Enter") {
    event.preventDefault();
    void handleOpenSelectedResult();
    return;
  }
  if (event.key === "Escape") {
    event.preventDefault();
    conversationStore.closeConversationSearch();
  }
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    conversationStore.openConversationSearch();
    return;
  }
  if (event.key === "Escape" && conversationStore.searchDialogVisible) {
    event.preventDefault();
    conversationStore.closeConversationSearch();
  }
}

watch(
  () => conversationStore.searchDialogVisible,
  (show) => {
    if (!show) {
      clearSearchTimer();
      return;
    }
    void nextTick(() => inputRef.value?.focus());
  }
);

watch(
  () => conversationStore.searchKeyword,
  () => {
    if (!conversationStore.searchDialogVisible) {
      return;
    }
    if (!conversationStore.searchKeyword.trim()) {
      clearSearchTimer();
      resetSearchResults();
      return;
    }
    scheduleSearch();
  }
);

watch(
  () => agentCatalogStore.selectedAgentUid,
  () => {
    if (!conversationStore.searchDialogVisible) {
      return;
    }
    clearSearchTimer();
    conversationStore.searchKeyword = "";
    resetSearchResults();
  }
);

onMounted(() => {
  window.addEventListener("keydown", handleGlobalKeydown);
});

onBeforeUnmount(() => {
  clearSearchTimer();
  window.removeEventListener("keydown", handleGlobalKeydown);
});
</script>

<template>
  <n-modal
    :show="conversationStore.searchDialogVisible"
    preset="card"
    class="conversation-search-modal"
    :mask-closable="true"
    :closable="false"
    :bordered="false"
    style="width: min(720px, 92vw)"
    @update:show="handleDialogVisibleChange"
  >
    <div class="conversation-search-shell">
      <div class="conversation-search-header">
        <div class="conversation-search-title">{{ t("chat.sidebar.searchHistoryTitle") }}</div>
        <button
          type="button"
          class="conversation-search-close"
          :aria-label="t('common.close')"
          @click="conversationStore.closeConversationSearch()"
        >
          <X :size="28" />
        </button>
      </div>

      <div class="conversation-search-toolbar">
        <n-input
          ref="inputRef"
          v-model:value="conversationStore.searchKeyword"
          size="large"
          clearable
          :placeholder="t('chat.sidebar.searchHistoryPlaceholder')"
          @keydown="handleInputKeydown"
        >
          <template #prefix>
            <Search :size="18" />
          </template>
        </n-input>
      </div>

      <div class="conversation-search-body">
        <div v-if="conversationStore.searchResults.length" class="conversation-search-list">
          <button
            v-for="(item, index) in conversationStore.searchResults"
            :key="`${item.conversationUid}:${item.resultTime}`"
            type="button"
            class="conversation-search-item"
            :class="{ active: conversationStore.searchSelectedIndex === index }"
            @mouseenter="conversationStore.searchSelectedIndex = index"
            @click="conversationStore.openConversationSearchResult(item)"
          >
            <div class="conversation-search-item-icon">
              <MessageCircle :size="18" />
            </div>
            <div class="conversation-search-item-content">
              <div class="conversation-search-item-title">{{ item.title || t("chat.sidebar.unnamed") }}</div>
              <div class="conversation-search-item-preview">{{ item.previewText }}</div>
            </div>
            <div class="conversation-search-item-time">{{ formatFriendlyDateTime(item.resultTime, "--") }}</div>
          </button>
        </div>
        <div
          v-else-if="conversationStore.searchLoading"
          class="conversation-search-empty"
        >
          {{ t("chat.sidebar.searchHistoryLoading") }}
        </div>
        <div
          v-else-if="conversationStore.searchKeyword.trim()"
          class="conversation-search-empty"
        >
          {{ t("chat.sidebar.searchHistoryEmpty") }}
        </div>
        <div v-else class="conversation-search-empty">
          {{ t("chat.sidebar.searchHistoryHint") }}
        </div>
      </div>

      <div v-if="conversationStore.searchHasMore" class="conversation-search-footer">
        <n-button
          quaternary
          :loading="conversationStore.searchLoading"
          @click="conversationStore.loadMoreSearchResults()"
        >
          {{ t("chat.sidebar.loadMore") }}
        </n-button>
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
.conversation-search-shell {
  display: flex;
  max-height: min(86vh, 920px);
  min-height: 620px;
  flex-direction: column;
  overflow: hidden;
}

.conversation-search-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 8px 18px;
}

.conversation-search-title {
  font-size: 20px;
  font-weight: 600;
}

.conversation-search-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--color-text-subtle);
  cursor: pointer;
}

.conversation-search-close:hover {
  background: var(--color-bg-soft-active);
  color: var(--color-text-primary);
}

.conversation-search-toolbar {
  padding: 0 8px 18px;
}

.conversation-search-body {
  flex: 1;
  min-height: 0;
  padding: 0 8px 8px;
  overflow: auto;
}

.conversation-search-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conversation-search-item {
  display: flex;
  align-items: center;
  gap: 18px;
  width: 100%;
  padding: 16px 24px;
  border: 0;
  border-radius: 24px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.conversation-search-item:hover,
.conversation-search-item.active {
  background: var(--color-bg-soft-active);
}

.conversation-search-item-icon {
  display: flex;
  flex: 0 0 40px;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
}

.conversation-search-item-content {
  flex: 1;
  min-width: 0;
}

.conversation-search-item-title {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-search-item-preview {
  display: -webkit-box;
  margin-top: 6px;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.conversation-search-item-time {
  flex: 0 0 auto;
  color: var(--color-text-subtle);
  font-size: 14px;
}

.conversation-search-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--color-text-subtle);
  text-align: center;
}

.conversation-search-footer {
  display: flex;
  justify-content: center;
  padding: 12px 8px 4px;
}
</style>
