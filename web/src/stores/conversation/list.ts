import { buildConversationPageRequestKey, dedupeConversations, reuseStableConversationSummaries } from "./helpers";
import type { ConversationMessagesModule, ConversationRuntimeModule, ConversationStoreContext, ConversationListModule } from "./types";

const CONVERSATION_PAGE_SIZE = 20;
const CONVERSATION_PAGE_FRESH_MS = 1000;

export function createConversationListModule(
  ctx: ConversationStoreContext,
  deps: {
    messagesModule: () => ConversationMessagesModule;
    runtimeModule: () => ConversationRuntimeModule;
    disconnectEventSource: () => void;
    syncRuntimeModelSelection: () => void;
    loadModelConfig: () => Promise<void>;
  }
): ConversationListModule {
  const { state, deps: storeDeps, internals } = ctx;

  function hasTrackedConversationStatus() {
    return state.conversations.value.some((item) => item.running || item.waitingApproval);
  }

  function stopConversationSummaryPolling() {
    if (internals.conversationSummaryPollTimer !== null) {
      window.clearTimeout(internals.conversationSummaryPollTimer);
      internals.conversationSummaryPollTimer = null;
    }
  }

  function scheduleConversationSummaryPolling() {
    stopConversationSummaryPolling();
    if (
      typeof window === "undefined"
      || !storeDeps.agentCatalogStore.selectedAgentUid
      || !hasTrackedConversationStatus()
    ) {
      return;
    }
    internals.conversationSummaryPollTimer = window.setTimeout(() => {
      void pollConversationSummaries();
    }, 5000);
  }

  function patchConversationListWithLatest(nextConversations: any[]) {
    state.conversations.value = reuseStableConversationSummaries(
      state.conversations.value,
      dedupeConversations(nextConversations)
    );
  }

  async function fetchConversationPage(params: { beforeSortKey?: string | null; asOf?: string }) {
    const requestParams = {
      agentUid: storeDeps.agentCatalogStore.selectedAgentUid || "",
      limit: CONVERSATION_PAGE_SIZE,
      beforeSortKey: params.beforeSortKey || undefined,
      asOf: params.asOf
    };
    const requestKey = buildConversationPageRequestKey(requestParams);
    const inFlight = internals.conversationPageRequests.get(requestKey);
    if (inFlight) {
      return inFlight;
    }
    const request = storeDeps.conversationApi.listConversationPage(requestParams)
      .finally(() => {
        if (internals.conversationPageRequests.get(requestKey) === request) {
          internals.conversationPageRequests.delete(requestKey);
        }
      });
    internals.conversationPageRequests.set(requestKey, request);
    return request;
  }

  function markConversationPageLoaded() {
    internals.lastConversationPageLoadedAt = Date.now();
    internals.lastConversationPageAgentUid = String(storeDeps.agentCatalogStore.selectedAgentUid || "").trim();
    internals.conversationPageLoadedOnce = true;
  }

  function canReuseFreshConversationPage(force: boolean) {
    if (force || !internals.conversationPageLoadedOnce) {
      return false;
    }
    const currentAgentUid = String(storeDeps.agentCatalogStore.selectedAgentUid || "").trim();
    if (!currentAgentUid || currentAgentUid !== internals.lastConversationPageAgentUid) {
      return false;
    }
    return Date.now() - internals.lastConversationPageLoadedAt < CONVERSATION_PAGE_FRESH_MS;
  }

  async function syncConversationSummaries() {
    const latestPage = await fetchConversationPage({});
    markConversationPageLoaded();
    patchConversationListWithLatest(latestPage.items);
    storeDeps.agentCatalogStore.ensureSelection();

    const knownConversationUids = new Set([
      ...state.conversations.value.map((item) => item.conversationUid),
      ...latestPage.items.map((item) => item.conversationUid)
    ]);
    if (state.currentConversationUid.value && !knownConversationUids.has(state.currentConversationUid.value)) {
      state.currentConversationUid.value = null;
      state.runningConversationUids.value = {};
      deps.messagesModule().resetMessageState();
      deps.runtimeModule().resetRuntimePanels();
      deps.disconnectEventSource();
      storeDeps.saveChatLastViewState({ mode: "draft", conversationUid: null });
      deps.syncRuntimeModelSelection();
      return;
    }

    if (state.currentConversationUid.value && isConversationRunningLocally(state.currentConversationUid.value)) {
      const currentSummary = state.conversations.value.find((item) => item.conversationUid === state.currentConversationUid.value)
        || latestPage.items.find((item) => item.conversationUid === state.currentConversationUid.value);
      if (currentSummary && !currentSummary.running) {
        clearConversationRunningLocally(currentSummary.conversationUid);
      }
    }
  }

  async function pollConversationSummaries() {
    if (internals.conversationSummaryPolling) {
      scheduleConversationSummaryPolling();
      return;
    }
    internals.conversationSummaryPolling = true;
    try {
      await syncConversationSummaries();
    } catch {
      // best effort: next polling cycle will retry
    } finally {
      internals.conversationSummaryPolling = false;
      scheduleConversationSummaryPolling();
    }
  }

  async function loadConversationSummaries(force = false) {
    if (canReuseFreshConversationPage(force)) {
      scheduleConversationSummaryPolling();
      return;
    }
    state.conversationListLoading.value = true;
    try {
      const page = await fetchConversationPage({});
      state.conversations.value = page.items;
      state.conversationListAsOf.value = page.asOf;
      state.conversationListCursor.value = page.nextBeforeSortKey || null;
      state.conversationListHasMore.value = Boolean(page.hasMore);
      markConversationPageLoaded();
    } finally {
      state.conversationListLoading.value = false;
      scheduleConversationSummaryPolling();
    }
  }

  async function loadMoreConversations() {
    if (state.conversationListLoading.value || !state.conversationListHasMore.value) {
      return;
    }
    state.conversationListLoading.value = true;
    try {
      const page = await fetchConversationPage({
        beforeSortKey: state.conversationListCursor.value,
        asOf: state.conversationListAsOf.value || undefined
      });
      state.conversations.value = reuseStableConversationSummaries(
        state.conversations.value,
        dedupeConversations([...state.conversations.value, ...page.items])
      );
      state.conversationListCursor.value = page.nextBeforeSortKey || null;
      state.conversationListHasMore.value = Boolean(page.hasMore);
    } finally {
      state.conversationListLoading.value = false;
    }
  }

  function patchConversationSummaryLocally(conversationUid: string, patch: Record<string, any>) {
    state.conversations.value = state.conversations.value.map((item) =>
      item.conversationUid === conversationUid
        ? { ...item, ...patch }
        : item
    );
  }

  function markConversationRunningLocally(conversationUid: string) {
    const normalizedConversationUid = String(conversationUid || "").trim();
    if (!normalizedConversationUid || state.runningConversationUids.value[normalizedConversationUid]) {
      return;
    }
    state.runningConversationUids.value = {
      ...state.runningConversationUids.value,
      [normalizedConversationUid]: true
    };
  }

  function clearConversationRunningLocally(conversationUid: string) {
    const normalizedConversationUid = String(conversationUid || "").trim();
    if (!normalizedConversationUid || !state.runningConversationUids.value[normalizedConversationUid]) {
      return;
    }
    const nextRunningConversationUids = { ...state.runningConversationUids.value };
    delete nextRunningConversationUids[normalizedConversationUid];
    state.runningConversationUids.value = nextRunningConversationUids;
  }

  function isConversationRunningLocally(conversationUid: string | null | undefined) {
    const normalizedConversationUid = String(conversationUid || "").trim();
    return Boolean(normalizedConversationUid && state.runningConversationUids.value[normalizedConversationUid]);
  }

  function finalizeActiveConversationSummary(conversationUid: string) {
    const currentSummary = state.conversations.value.find((item) => item.conversationUid === conversationUid);
    if (!currentSummary) {
      return;
    }
    patchConversationSummaryLocally(conversationUid, {
      running: false,
      waitingApproval: false,
      unread: false
    });
  }

  async function refreshConversations(
    preferredConversationUid: string | null = state.currentConversationUid.value,
    withLoading = true,
    force = false
  ) {
    if (withLoading) {
      state.loading.value = true;
    }
    try {
      await loadConversationSummaries(force);

      if (!state.filteredConversations.value.length) {
        state.currentConversationUid.value = null;
        state.runningConversationUids.value = {};
        deps.messagesModule().resetMessageState();
        deps.runtimeModule().resetRuntimePanels();
        deps.disconnectEventSource();
        stopConversationSummaryPolling();
        storeDeps.saveChatLastViewState({ mode: "draft", conversationUid: null });
        deps.syncRuntimeModelSelection();
        return;
      }

      const nextConversationUid = state.filteredConversations.value.some((item) => item.conversationUid === preferredConversationUid)
        ? preferredConversationUid
        : state.filteredConversations.value[0].conversationUid;

      if (nextConversationUid && nextConversationUid !== state.currentConversationUid.value) {
        await deps.messagesModule().selectConversation(nextConversationUid);
        return;
      }

      deps.syncRuntimeModelSelection();
    } finally {
      if (withLoading) {
        state.loading.value = false;
      }
    }
  }

  async function init() {
    storeDeps.agentCatalogStore.restoreSelection();
    const lastViewState = storeDeps.loadChatLastViewState();
    await Promise.all([storeDeps.agentCatalogStore.loadCatalog(), deps.loadModelConfig()]);
    state.loading.value = true;
    try {
      await loadConversationSummaries(true);

      if (!state.filteredConversations.value.length) {
        deps.messagesModule().startDraftConversation();
        return;
      }

      if (lastViewState?.mode === "draft") {
        deps.messagesModule().startDraftConversation();
        return;
      }

      const preferredConversationUid = lastViewState?.mode === "conversation"
        ? (lastViewState.conversationUid || null)
        : null;
      const nextConversationUid = state.filteredConversations.value.some((item) => item.conversationUid === preferredConversationUid)
        ? preferredConversationUid
        : state.filteredConversations.value[0].conversationUid;

      if (nextConversationUid) {
        await deps.messagesModule().selectConversation(nextConversationUid);
      } else {
        deps.messagesModule().startDraftConversation();
      }
    } finally {
      state.loading.value = false;
    }
  }

  return {
    stopConversationSummaryPolling,
    scheduleConversationSummaryPolling,
    loadConversationSummaries,
    loadMoreConversations,
    refreshConversations,
    init,
    patchConversationSummaryLocally,
    finalizeActiveConversationSummary,
    markConversationRunningLocally,
    clearConversationRunningLocally,
    isConversationRunningLocally
  };
}
