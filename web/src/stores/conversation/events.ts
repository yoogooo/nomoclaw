import type {
  ConversationEventsModule,
  ConversationListModule,
  ConversationMessagesModule,
  ConversationRuntimeModule,
  ConversationStoreContext
} from "./types";

const MAX_RECENT_EVENT_IDS = 200;

export function createConversationEventsModule(
  ctx: ConversationStoreContext,
  deps: {
    listModule: () => ConversationListModule;
    messagesModule: () => ConversationMessagesModule;
    runtimeModule: () => ConversationRuntimeModule;
  }
): ConversationEventsModule {
  const { state, deps: storeDeps, internals } = ctx;

  function disconnectEventSource() {
    if (internals.eventSource) {
      internals.eventSource.close();
      internals.eventSource = null;
    }
    internals.subscribedConversationUid = null;
  }

  function rememberEventId(eventId?: string) {
    const normalized = String(eventId || "").trim();
    if (!normalized || internals.recentEventIdSet.has(normalized)) {
      return false;
    }
    internals.recentEventIds.push(normalized);
    internals.recentEventIdSet.add(normalized);
    if (internals.recentEventIds.length > MAX_RECENT_EVENT_IDS) {
      const removed = internals.recentEventIds.shift();
      if (removed) {
        internals.recentEventIdSet.delete(removed);
      }
    }
    return true;
  }

  function subscribeEvents() {
    const conversationUid = String(state.currentConversationUid.value || "").trim();
    if (!conversationUid) {
      return;
    }
    if (internals.subscribedConversationUid === conversationUid && internals.eventSource) {
      return;
    }
    disconnectEventSource();
    internals.subscribedConversationUid = conversationUid;
    internals.eventSource = storeDeps.subscribeConversationEvents(conversationUid, (event) => {
      void handleEvent(event);
    });
  }

  async function handleEvent(event: any) {
    if (event.id && !rememberEventId(event.id)) {
      return;
    }
    const runtimeModule = deps.runtimeModule();
    const listModule = deps.listModule();
    const messagesModule = deps.messagesModule();
    const type = event.eventType;
    if (type === "MESSAGE_DELTA") {
      if (!event.messageUid) return;
      const textDelta = String(event.payload.textDelta || "");
      const done = Boolean(event.payload.done);
      const accumulatedText = event.payload.accumulatedText === undefined ? undefined : String(event.payload.accumulatedText || "");
      if (!textDelta && !done && accumulatedText === undefined) return;
      runtimeModule.upsertStreamingAssistantDelta(event.messageUid, textDelta, event.timestamp, accumulatedText, done);
      return;
    }
    if (type === "PLAN_CREATED") {
      if (event.messageUid) {
        runtimeModule.clearStreamingAssistantDraft(event.messageUid);
      }
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.modelToolCall", { round: event.payload.roundIndex || 1 }));
      runtimeModule.renderPlanSteps(event.payload.steps || []);
      runtimeModule.handlePlanCreated(event);
      return;
    }
    if (type === "MESSAGE_REASONING") {
      runtimeModule.handleReasoningEvent(event);
      return;
    }
    if (type === "STEP_WAITING_APPROVAL") {
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.stepWaitingApproval", { stepUid: event.stepUid }));
      runtimeModule.handleRunStepEvent(event);
      runtimeModule.showApprovalAlert(event);
      if (event.conversationUid) {
        listModule.patchConversationSummaryLocally(event.conversationUid, {
          waitingApproval: true,
          running: true
        });
        listModule.markConversationRunningLocally(event.conversationUid);
      }
      listModule.scheduleConversationSummaryPolling();
      return;
    }
    if (type === "STEP_STARTED") {
      if (state.approval.value.stepUid && state.approval.value.stepUid === event.stepUid) {
        runtimeModule.clearApproval();
      }
      if (event.conversationUid) {
        listModule.patchConversationSummaryLocally(event.conversationUid, {
          waitingApproval: false,
          running: true
        });
        listModule.markConversationRunningLocally(event.conversationUid);
      }
      runtimeModule.updateBrowserRuntimeOverlayFromStepEvent(event);
      if (!Boolean(event.payload.silentLog)) {
        storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.stepStarted", {
          round: event.payload.roundIndex || 1,
          title: event.payload.title
        }));
      }
      runtimeModule.handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_FINISHED") {
      if (state.browserRuntimeOverlay.value.stepUid && state.browserRuntimeOverlay.value.stepUid === (event.stepUid || null)) {
        runtimeModule.clearBrowserRuntimeOverlay();
      }
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.stepFinished", {
        title: event.payload.title,
        output: event.payload.output || ""
      }));
      runtimeModule.handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_FAILED") {
      if (state.browserRuntimeOverlay.value.stepUid && state.browserRuntimeOverlay.value.stepUid === (event.stepUid || null)) {
        runtimeModule.clearBrowserRuntimeOverlay();
      }
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.stepFailed", {
        title: event.payload.title,
        errorMessage: event.payload.errorMessage || ""
      }));
      runtimeModule.handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_REJECTED") {
      if (state.browserRuntimeOverlay.value.stepUid && state.browserRuntimeOverlay.value.stepUid === (event.stepUid || null)) {
        runtimeModule.clearBrowserRuntimeOverlay();
      }
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.stepRejected", { title: event.payload.title }));
      runtimeModule.handleRunStepEvent(event);
      runtimeModule.clearApproval();
      if (event.conversationUid) {
        listModule.patchConversationSummaryLocally(event.conversationUid, { waitingApproval: false });
      }
      listModule.scheduleConversationSummaryPolling();
      return;
    }
    if (type === "ROUND_TOKEN_USAGE") {
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.roundTokenUsage", {
        round: Number(event.payload.roundIndex || 1),
        input: Number(event.payload.inputTokens || 0),
        cachedInput: Number(event.payload.cachedInputTokens || 0),
        output: Number(event.payload.outputTokens || 0),
        total: Number(event.payload.totalTokens || 0),
        modelName: event.payload.modelName || "-"
      }));
      return;
    }
    if (type === "MESSAGE_COMPLETED") {
      runtimeModule.clearApproval();
      runtimeModule.clearBrowserRuntimeOverlay();
      if (event.messageUid) {
        runtimeModule.clearStreamingAssistantDraft(event.messageUid);
      }
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.messageCompleted", { status: event.payload.status, message: event.payload.message }));
      if (event.payload.stopReason) {
        storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.stopReason", {
          reason: event.payload.stopReason,
          roundsUsed: event.payload.roundsUsed,
          maxRounds: event.payload.maxRounds
        }));
      }
      if (event.conversationUid) {
        listModule.clearConversationRunningLocally(event.conversationUid);
      }
      if (state.currentConversationUid.value) {
        const activeConversationUid = state.currentConversationUid.value;
        await messagesModule.refreshLatestMessages(activeConversationUid);
        if (!state.skipConversationListRefresh.value) {
          listModule.finalizeActiveConversationSummary(activeConversationUid);
        }
        await messagesModule.keepCurrentConversationRead(activeConversationUid);
      } else {
        await listModule.loadConversationSummaries(true);
      }
      listModule.scheduleConversationSummaryPolling();
      return;
    }
    if (type === "LOOP_LIMIT_REACHED") {
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.loopLimitReached", {
        maxRounds: event.payload.maxRounds,
        failedStepId: event.payload.failedStepId || "-"
      }));
      return;
    }
    if (type === "MESSAGE_CANCELED") {
      runtimeModule.clearApproval();
      runtimeModule.clearBrowserRuntimeOverlay();
      if (event.messageUid) {
        runtimeModule.clearStreamingAssistantDraft(event.messageUid);
      }
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.messageCanceled"));
      const latestUserMessage = [...state.messages.value].reverse().find((item) => item.role === "user" && item.messageUid);
      if (latestUserMessage?.messageUid) {
        storeDeps.conversationRunsStore.markRunCanceled(latestUserMessage.messageUid);
      }
      if (event.conversationUid) {
        listModule.clearConversationRunningLocally(event.conversationUid);
      }
      if (state.currentConversationUid.value) {
        const activeConversationUid = state.currentConversationUid.value;
        await messagesModule.refreshLatestMessages(activeConversationUid);
        if (!state.skipConversationListRefresh.value) {
          listModule.finalizeActiveConversationSummary(activeConversationUid);
        }
        await messagesModule.keepCurrentConversationRead(activeConversationUid);
      } else {
        await listModule.loadConversationSummaries(true);
      }
      listModule.scheduleConversationSummaryPolling();
    }
  }

  return {
    subscribeEvents,
    disconnectEventSource,
    handleEvent
  };
}
