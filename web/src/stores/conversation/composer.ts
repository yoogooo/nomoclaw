import { normalizeUploadPolicy, validateFilesAgainstPolicy } from "./uploadPolicy";
import { findAgentDefaultModel, findLatestMessageModelSelection, splitModelKey } from "./modelSelection";
import type {
  ConversationEventsModule,
  ConversationListModule,
  ConversationMessagesModule,
  ConversationRuntimeModule,
  ConversationComposerModule,
  ConversationStoreContext
} from "./types";

export function createConversationComposerModule(
  ctx: ConversationStoreContext,
  deps: {
    listModule: () => ConversationListModule;
    messagesModule: () => ConversationMessagesModule;
    runtimeModule: () => ConversationRuntimeModule;
    eventsModule: () => ConversationEventsModule;
  }
): ConversationComposerModule {
  const { state, deps: storeDeps } = ctx;

  function hasConfiguredModel(modelProvider: string, modelName: string) {
    if (!modelProvider || !modelName) {
      return false;
    }
    const provider = state.configuredProviders.value.find((item) => item.id === modelProvider);
    if (!provider) {
      return false;
    }
    return provider.models.some((model: any) => model.id === modelName);
  }

  async function promptModelSetupGuide() {
    storeDeps.modelGateStore.resetPrompt();
    if (storeDeps.modelGateStore.checking) {
      return;
    }
    await storeDeps.modelGateStore.refreshModelReadiness({
      suppressErrorToast: true
    });
  }

  async function guideToModelSetup() {
    await promptModelSetupGuide();
    if (storeDeps.modelGateStore.checkErrorKind !== "none") {
      const errorKey = (
        storeDeps.modelGateStore.checkErrorKind === "network"
        || storeDeps.modelGateStore.checkErrorKind === "backendUnavailable"
      )
        ? "http.serviceNotStarted"
        : "http.500";
      storeDeps.message.error(storeDeps.tr(errorKey));
      return;
    }
    storeDeps.message.warning(storeDeps.tr("modelGate.status.missingModel"));
  }

  function syncRuntimeModelSelection(sourceMessages = state.messages.value) {
    const latestMessageModel = findLatestMessageModelSelection(sourceMessages, state.configuredProviders.value as any);
    if (latestMessageModel) {
      state.selectedModelProvider.value = latestMessageModel.modelProvider;
      state.selectedModelName.value = latestMessageModel.modelName;
      return;
    }
    const fallback = findAgentDefaultModel(
      storeDeps.agentCatalogStore.selectedAgentUid,
      storeDeps.agentCatalogStore.allAgents,
      state.configuredProviders.value as any
    );
    state.selectedModelProvider.value = fallback.modelProvider;
    state.selectedModelName.value = fallback.modelName;
  }

  async function loadModelConfig() {
    state.modelConfig.value = await storeDeps.modelApi.getAvailableModelConfig();
    syncRuntimeModelSelection();
  }

  function clearDraftAttachments(reason?: string) {
    state.draftAttachments.value = [];
    if (reason) {
      storeDeps.message.warning(reason);
    }
  }

  async function ensureConversationForInteraction() {
    if (state.currentConversationUid.value) {
      return state.currentConversationUid.value;
    }

    const created = await storeDeps.conversationApi.createConversation("", storeDeps.agentCatalogStore.selectedAgentUid);
    state.currentConversationUid.value = created.conversationUid;
    await deps.listModule().refreshConversations(created.conversationUid, false, true);
    deps.eventsModule().subscribeEvents();
    return created.conversationUid;
  }

  async function ensureConversationForMessageSend() {
    if (state.currentConversationUid.value) {
      return state.currentConversationUid.value;
    }

    const created = await storeDeps.conversationApi.createConversation("", storeDeps.agentCatalogStore.selectedAgentUid);
    const conversationUid = created.conversationUid;
    state.currentConversationUid.value = conversationUid;
    deps.runtimeModule().restoreApprovalModeForConversation(conversationUid);
    storeDeps.saveChatLastViewState({ mode: "conversation", conversationUid });
    deps.eventsModule().disconnectEventSource();
    deps.eventsModule().subscribeEvents();
    void deps.listModule().refreshConversations(conversationUid, false, true);
    return conversationUid;
  }

  async function changeRuntimeModel(value: string) {
    const { modelProvider, modelName } = splitModelKey(value);
    if (!modelProvider || !modelName) {
      return;
    }
    if (modelProvider === state.selectedModelProvider.value && modelName === state.selectedModelName.value) {
      return;
    }
    const nextOption = state.configuredProviders.value
      .find((provider) => provider.id === modelProvider)
      ?.models.find((model: any) => model.id === modelName);
    const nextPolicy = normalizeUploadPolicy(nextOption?.uploadPolicy);
    if (state.draftAttachments.value.length) {
      const existingFits = (() => {
        try {
          validateFilesAgainstPolicy(nextPolicy, state.draftAttachments.value, [], storeDeps.tr);
          return true;
        } catch {
          return false;
        }
      })();
      if (!existingFits) {
        clearDraftAttachments(storeDeps.tr("chat.composer.clearedByModelSwitch"));
      }
    }
    state.selectedModelProvider.value = modelProvider;
    state.selectedModelName.value = modelName;
  }

  async function uploadFiles(fileList: FileList | File[]) {
    const files = Array.from(fileList || []).filter(Boolean);
    if (!files.length) {
      return [];
    }
    if (!state.hasAnyConfiguredModel.value) {
      await guideToModelSetup();
      return [];
    }
    try {
      validateFilesAgainstPolicy(state.currentUploadPolicy.value, state.draftAttachments.value, files, storeDeps.tr);
    } catch (error) {
      storeDeps.message.error(error instanceof Error ? error.message : storeDeps.tr("toast.uploadRuleNotMatch"));
      return [];
    }
    const lockedProvider = state.selectedModelProvider.value;
    const lockedModelName = state.selectedModelName.value;
    const conversationUid = await ensureConversationForInteraction();
    const effectiveProvider = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedProvider : state.selectedModelProvider.value;
    const effectiveModelName = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedModelName : state.selectedModelName.value;
    if (effectiveProvider && effectiveModelName) {
      state.selectedModelProvider.value = effectiveProvider;
      state.selectedModelName.value = effectiveModelName;
    }
    state.uploadingFiles.value = true;
    try {
      const uploaded = await storeDeps.conversationApi.uploadConversationFiles(conversationUid, {
        files,
        modelProvider: effectiveProvider,
        modelName: effectiveModelName
      });
      state.draftAttachments.value = [...state.draftAttachments.value, ...uploaded.items];
      return uploaded.items;
    } finally {
      state.uploadingFiles.value = false;
    }
  }

  function removeDraftAttachment(fileUrl: string) {
    state.draftAttachments.value = state.draftAttachments.value.filter((item) => item.fileUrl !== fileUrl);
  }

  async function sendMessage() {
    if (deps.listModule().isConversationRunningLocally(state.currentConversationUid.value)) {
      await cancelRunningMessage();
      return;
    }

    const content = state.draftMessage.value.trim();
    if (!content) {
      return;
    }
    if (!state.selectedModelProvider.value || !state.selectedModelName.value) {
      if (!state.hasAnyConfiguredModel.value) {
        await guideToModelSetup();
        return;
      }
      storeDeps.message.error(storeDeps.tr("toast.chooseModelFirst"));
      return;
    }

    const lockedProvider = state.selectedModelProvider.value;
    const lockedModelName = state.selectedModelName.value;
    const effectiveProvider = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedProvider : state.selectedModelProvider.value;
    const effectiveModelName = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedModelName : state.selectedModelName.value;
    if (!effectiveProvider || !effectiveModelName) {
      if (!state.hasAnyConfiguredModel.value) {
        await guideToModelSetup();
        return;
      }
      storeDeps.message.error(storeDeps.tr("toast.chooseModelFirst"));
      return;
    }
    state.selectedModelProvider.value = effectiveProvider;
    state.selectedModelName.value = effectiveModelName;
    const attachments = [...state.draftAttachments.value];
    const tempMessage = {
      role: "user",
      content,
      status: "CREATED",
      provider: effectiveProvider,
      modelName: effectiveModelName,
      createdTime: new Date().toISOString(),
      attachments
    } as any;

    state.messages.value = [...state.messages.value, tempMessage];
    state.draftMessage.value = "";
    state.draftAttachments.value = [];

    try {
      const conversationUid = await ensureConversationForMessageSend();
      const accepted = await storeDeps.conversationApi.sendMessage(conversationUid, {
        message: content,
        fileUrls: attachments.map((item) => item.fileUrl),
        modelProvider: effectiveProvider,
        modelName: effectiveModelName,
        approvalMode: state.approvalMode.value
      });
      tempMessage.messageUid = accepted.messageUid;
      deps.listModule().markConversationRunningLocally(conversationUid);
      const lastUserMessageTime = tempMessage.createdTime || new Date().toISOString();
      deps.listModule().patchConversationSummaryLocally(conversationUid, {
        running: true,
        waitingApproval: false,
        unread: false,
        lastUserMessageTime
      });
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.messageSubmitted", { messageUid: accepted.messageUid }));
      await deps.listModule().refreshConversations(conversationUid, false, true);
      deps.listModule().scheduleConversationSummaryPolling();
    } catch (error) {
      state.messages.value = state.messages.value.filter((item) => item !== tempMessage);
      state.draftMessage.value = content;
      state.draftAttachments.value = attachments;
      throw error;
    }
  }

  async function cancelRunningMessage() {
    if (!state.currentConversationUid.value) return;
    const activeConversationUid = state.currentConversationUid.value;
    deps.messagesModule().markConversationReadLocally(activeConversationUid);
    await storeDeps.conversationApi.cancelConversation(activeConversationUid);
    await deps.messagesModule().keepCurrentConversationRead(activeConversationUid);
    deps.listModule().clearConversationRunningLocally(activeConversationUid);
    deps.listModule().scheduleConversationSummaryPolling();
  }

  async function decideStep(action: "allow" | "deny", scope: "once" | "session" | "agent" | "user") {
    if (!state.currentConversationUid.value || !state.approval.value.stepUid || state.approval.value.submitting) return;
    const stepUid = state.approval.value.stepUid;
    const activeConversationUid = String(state.currentConversationUid.value || "").trim();
    const previousUnread = activeConversationUid
      ? state.conversations.value.find((item) => item.conversationUid === activeConversationUid)?.unread
      : undefined;
    const submittingAction = action === "deny"
      ? "deny_once"
      : (scope === "session" ? "allow_session"
        : scope === "agent" ? "allow_agent"
          : scope === "user" ? "allow_user" : "allow_once");
    state.approval.value = {
      ...state.approval.value,
      submitting: true,
      submittingAction
    };
    try {
      await storeDeps.conversationApi.decideStep(state.currentConversationUid.value, stepUid, { action, scope });
      deps.runtimeModule().clearApproval();
      if (activeConversationUid) {
        state.conversations.value = state.conversations.value.map((item) =>
          item.conversationUid === activeConversationUid
            ? { ...item, waitingApproval: false, unread: previousUnread ?? item.unread }
            : item
        );
        if (previousUnread === false) {
          void storeDeps.conversationApi.markConversationRead(activeConversationUid).catch(() => {
            // best effort: next refresh will reconcile read state
          });
        }
      }
      if (action === "allow") {
        storeDeps.message.success(storeDeps.tr("toast.approveSuccess"));
      } else {
        storeDeps.message.warning(storeDeps.tr("toast.rejectSuccess"));
      }
      storeDeps.runtimeLogStore.append(storeDeps.tr("chat.runtime.approvalSubmitted", { action: `${action}:${scope}`, stepUid }));
      deps.listModule().scheduleConversationSummaryPolling();
    } catch (error) {
      state.approval.value = {
        ...state.approval.value,
        submitting: false,
        submittingAction: null
      };
      storeDeps.message.error(storeDeps.tr(action === "allow" ? "toast.approveFailed" : "toast.rejectFailed"));
      throw error;
    }
  }

  async function approveStep(scope: "once" | "session" | "agent" | "user" = "once") {
    await decideStep("allow", scope);
  }

  async function rejectStep() {
    await decideStep("deny", "once");
  }

  return {
    loadModelConfig,
    syncRuntimeModelSelection,
    hasConfiguredModel,
    guideToModelSetup,
    changeRuntimeModel,
    uploadFiles,
    removeDraftAttachment,
    sendMessage,
    cancelRunningMessage,
    approveStep,
    rejectStep
  };
}
