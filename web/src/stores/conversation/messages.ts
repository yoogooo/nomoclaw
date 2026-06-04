import { dedupeMessages } from "./helpers";
import type {
  ConversationListModule,
  ConversationRuntimeModule,
  ConversationStoreContext,
  ConversationMessagesModule
} from "./types";

const MESSAGE_PAGE_SIZE = 20;

export function createConversationMessagesModule(
  ctx: ConversationStoreContext,
  deps: {
    listModule: () => ConversationListModule;
    runtimeModule: () => ConversationRuntimeModule;
    subscribeEvents: () => void;
    disconnectEventSource: () => void;
    syncRuntimeModelSelection: (sourceMessages?: any[]) => void;
  }
): ConversationMessagesModule {
  const { state, deps: storeDeps, internals } = ctx;

  function isInProgressMessageStatus(status?: string) {
    const normalized = (status || "").trim().toUpperCase();
    if (!normalized) {
      return false;
    }
    return !["COMPLETED", "FAILED", "CANCELED"].includes(normalized);
  }

  function reconcileRunningConversationByMessages(conversationUid: string, loadedMessages: any[]) {
    const latestUserMessage = [...loadedMessages]
      .reverse()
      .find((item) => item.role === "user" && item.messageUid);
    const isRunning = isInProgressMessageStatus(latestUserMessage?.status);
    if (isRunning) {
      state.runningConversationUid.value = conversationUid;
      return;
    }
    if (state.runningConversationUid.value === conversationUid) {
      state.runningConversationUid.value = null;
    }
  }

  function resetMessageState() {
    state.messages.value = [];
    state.messageHistoryCursor.value = null;
    state.messageHistoryHasMore.value = false;
    state.messageHistoryLoading.value = false;
    state.messageInitialLoaded.value = false;
  }

  async function loadMessages(conversationUid: string) {
    const token = ++internals.messageLoadToken;
    const [messagePage, runs] = await Promise.all([
      storeDeps.conversationApi.listMessagesPage(conversationUid, {
        limit: MESSAGE_PAGE_SIZE
      }),
      storeDeps.conversationApi.listMessageRuns(conversationUid)
    ]);
    if (token !== internals.messageLoadToken || state.currentConversationUid.value !== conversationUid) {
      return;
    }
    state.messages.value = dedupeMessages(messagePage.items);
    state.messageHistoryCursor.value = messagePage.nextBeforeMessageUid || null;
    state.messageHistoryHasMore.value = Boolean(messagePage.hasMore);
    state.messageHistoryLoading.value = false;
    state.messageInitialLoaded.value = true;
    reconcileRunningConversationByMessages(conversationUid, messagePage.items);
    storeDeps.conversationRunsStore.setRuns(runs);
    deps.runtimeModule().restoreApprovalFromRuns(runs);
  }

  async function loadOlderMessages() {
    const conversationUid = state.currentConversationUid.value;
    if (!conversationUid || !state.messageHistoryHasMore.value || state.messageHistoryLoading.value) {
      return;
    }
    state.messageHistoryLoading.value = true;
    try {
      const page = await storeDeps.conversationApi.listMessagesPage(conversationUid, {
        limit: MESSAGE_PAGE_SIZE,
        beforeMessageUid: state.messageHistoryCursor.value || undefined
      });
      if (state.currentConversationUid.value !== conversationUid) {
        return;
      }
      state.messages.value = dedupeMessages([...page.items, ...state.messages.value]);
      state.messageHistoryCursor.value = page.nextBeforeMessageUid || null;
      state.messageHistoryHasMore.value = Boolean(page.hasMore);
    } finally {
      if (state.currentConversationUid.value === conversationUid) {
        state.messageHistoryLoading.value = false;
      }
    }
  }

  async function refreshLatestMessages(conversationUid: string) {
    const page = await storeDeps.conversationApi.listMessagesPage(conversationUid, {
      limit: MESSAGE_PAGE_SIZE
    });
    if (state.currentConversationUid.value !== conversationUid) {
      return;
    }
    const olderMessages = state.messages.value.filter((item) => {
      if (!item.messageUid) {
        return false;
      }
      return !page.items.some((latest) => latest.messageUid && latest.messageUid === item.messageUid);
    });
    state.messages.value = dedupeMessages([...olderMessages, ...page.items]);
    state.messageHistoryHasMore.value = olderMessages.length > 0 ? state.messageHistoryHasMore.value : Boolean(page.hasMore);
    if (!olderMessages.length) {
      state.messageHistoryCursor.value = page.nextBeforeMessageUid || null;
    }
    reconcileRunningConversationByMessages(conversationUid, state.messages.value);
  }

  function startDraftConversation() {
    const previousMessages = [...state.messages.value];
    state.currentConversationUid.value = null;
    state.runningConversationUid.value = null;
    resetMessageState();
    state.draftMessage.value = "";
    state.draftAttachments.value = [];
    deps.runtimeModule().resetRuntimePanels();
    deps.disconnectEventSource();
    deps.listModule().scheduleConversationSummaryPolling();
    state.approvalMode.value = "default";
    storeDeps.saveChatLastViewState({ mode: "draft", conversationUid: null });
    deps.syncRuntimeModelSelection(previousMessages);
  }

  function markConversationReadLocally(conversationUid: string) {
    deps.listModule().patchConversationSummaryLocally(conversationUid, { unread: false });
  }

  async function keepCurrentConversationRead(conversationUid: string) {
    markConversationReadLocally(conversationUid);
    try {
      await storeDeps.conversationApi.markConversationRead(conversationUid);
      markConversationReadLocally(conversationUid);
    } catch {
      // best effort: next refresh will reconcile unread state
    }
  }

  async function selectConversation(conversationUid: string) {
    if (state.currentConversationUid.value !== conversationUid) {
      resetMessageState();
    }
    state.currentConversationUid.value = conversationUid;
    deps.runtimeModule().restoreApprovalModeForConversation(conversationUid);
    storeDeps.saveChatLastViewState({ mode: "conversation", conversationUid });
    state.draftAttachments.value = [];
    deps.runtimeModule().resetRuntimePanels();
    await loadMessages(conversationUid);
    const currentSummary = state.conversations.value.find((item) => item.conversationUid === conversationUid);
    if (currentSummary?.unread) {
      void storeDeps.conversationApi.markConversationRead(conversationUid).then(() => {
        state.conversations.value = state.conversations.value.map((item) =>
          item.conversationUid === conversationUid
            ? { ...item, unread: false }
            : item
        );
      }).catch(() => {
        // best effort: next refresh will reconcile unread state
      });
    }
    deps.syncRuntimeModelSelection();
    deps.subscribeEvents();
  }

  async function applyAgentSelection() {
    await deps.listModule().refreshConversations(state.currentConversationUid.value, true, true);
    if (!state.filteredConversations.value.length) {
      startDraftConversation();
      return;
    }
    deps.syncRuntimeModelSelection();
  }

  async function openFile(path: string) {
    await storeDeps.fileApi.openFile(path);
  }

  return {
    startDraftConversation,
    selectConversation,
    loadOlderMessages,
    refreshLatestMessages,
    applyAgentSelection,
    markConversationReadLocally,
    keepCurrentConversationRead,
    reconcileRunningConversationByMessages,
    resetMessageState,
    openFile
  };
}
