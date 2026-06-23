import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { conversationApi } from "@/api/conversationApi";
import { fileApi } from "@/api/fileApi";
import { subscribeConversationEvents } from "@/api/eventStreamApi";
import { modelApi } from "@/api/modelApi";
import { dialog, message, warningDialogPreset } from "@/discrete";
import { tr } from "@/i18n";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { loadChatLastViewState, saveChatLastViewState } from "@/stores/chatViewState";
import { useConversationRunsStore } from "@/stores/conversationRuns";
import { useModelGateStore } from "@/stores/modelGate";
import { useRuntimeLogStore } from "@/stores/runtimeLog";
import { resolveApprovalFromPayload, resolveApprovalFromStep } from "@/utils/approvalRenderer";
import { buildModelKey, isEmbeddingModel, isProviderConfigured } from "./modelSelection";
import { normalizeUploadPolicy } from "./uploadPolicy";
import { createConversationComposerModule } from "./composer";
import { createConversationEventsModule } from "./events";
import { createConversationListModule } from "./list";
import { createConversationMessagesModule } from "./messages";
import { createConversationRuntimeModule } from "./runtime";
import type { ConversationStoreContext, ConversationComposerModule, ConversationEventsModule, ConversationListModule, ConversationMessagesModule, ConversationRuntimeModule } from "./types";
import type { ApprovalMode, ConversationAttachment, ConversationMessage, ConversationSearchResult, ConversationSummary, ModelConfig, ModelProviderOption } from "@/types/api";

export const useConversationStore = defineStore("conversation", () => {
  const agentCatalogStore = useAgentCatalogStore();
  const runtimeLogStore = useRuntimeLogStore();
  const conversationRunsStore = useConversationRunsStore();
  const modelGateStore = useModelGateStore();

  const conversations = ref<ConversationSummary[]>([]);
  const conversationListAsOf = ref("");
  const conversationListCursor = ref<string | null>(null);
  const conversationListHasMore = ref(false);
  const conversationListLoading = ref(false);
  const searchDialogVisible = ref(false);
  const searchKeyword = ref("");
  const searchResults = ref<ConversationSearchResult[]>([]);
  const searchCursor = ref<string | null>(null);
  const searchHasMore = ref(false);
  const searchLoading = ref(false);
  const searchSelectedIndex = ref(0);
  const anchorMessageUid = ref("");
  const currentConversationUid = ref<string | null>(null);
  const runningConversationUids = ref<Record<string, true>>({});
  const messages = ref<ConversationMessage[]>([]);
  const messageHistoryCursor = ref<string | null>(null);
  const messageHistoryHasMore = ref(false);
  const messageHistoryLoading = ref(false);
  const messageInitialLoaded = ref(false);
  const draftMessage = ref("");
  const draftAttachments = ref<ConversationAttachment[]>([]);
  const uploadingFiles = ref(false);
  const loading = ref(false);
  const modelConfig = ref<ModelConfig>({ providers: [] });
  const selectedModelProvider = ref("");
  const selectedModelName = ref("");
  const approvalMode = ref<ApprovalMode>("default");
  const approval = ref({
    stepUid: null,
    title: "",
    body: "",
    labelKey: "chat.approval.commandLabel",
    command: "",
    toolName: "",
    policyReasonCode: "",
    riskLevel: "HIGH",
    submitting: false,
    submittingAction: null
  });
  const browserRuntimeOverlay = ref({
    visible: false,
    stepUid: null,
    attemptId: "",
    attemptIndex: 0,
    retryCount: 0,
    title: "",
    details: "",
    hint: "",
    downloadedBytes: 0,
    progressPercent: 0,
    indeterminate: false,
    currentArtifact: "",
    segmentPercent: -1,
    overallPercent: -1,
    completedArtifacts: []
  });
  const skipConversationListRefresh = ref(false);
  const streamingAssistantByParentUid = ref<Record<string, number>>({});

  const filteredConversations = computed(() => conversations.value);
  const currentConversation = computed(() =>
    conversations.value.find((item) => item.conversationUid === currentConversationUid.value) || null
  );
  const currentConversationTitle = computed(() => currentConversation.value?.title || "Conversation");
  const configuredProviders = computed(() =>
    modelConfig.value.providers
      .filter((provider) => isProviderConfigured(provider))
      .map((provider) => ({
        ...provider,
        models: provider.models.filter((model) => {
          if (!model.id?.trim()) {
            return false;
          }
          if (provider.local && isEmbeddingModel(model)) {
            return false;
          }
          return true;
        })
      }))
      .filter((provider) => provider.models.length)
  );
  const availableModelOptions = computed(() =>
    configuredProviders.value
      .map((provider) => ({
        label: provider.name,
        key: provider.id,
        children: provider.models.map((model) => ({
          label: model.name || model.id,
          value: buildModelKey(provider.id, model.id),
          providerId: provider.id,
          providerLabel: provider.name,
          capabilityTags: model.capabilities || []
        }))
      }))
      .filter((group) => group.children.length)
  );
  const hasAnyConfiguredModel = computed(() => configuredProviders.value.length > 0);
  const currentModelOption = computed<ModelProviderOption | null>(() => {
    if (!selectedModelProvider.value || !selectedModelName.value) {
      return null;
    }
    const provider = configuredProviders.value.find((item) => item.id === selectedModelProvider.value);
    return provider?.models.find((item) => item.id === selectedModelName.value) || null;
  });
  const selectedModelKey = computed(() =>
    selectedModelProvider.value && selectedModelName.value
      ? buildModelKey(selectedModelProvider.value, selectedModelName.value)
      : ""
  );
  const currentUploadPolicy = computed(() => normalizeUploadPolicy(currentModelOption.value?.uploadPolicy));
  const uploadDisabledReason = computed(() => {
    if (!selectedModelProvider.value || !selectedModelName.value) {
      return tr("toast.chooseModelFirst");
    }
    if (!currentUploadPolicy.value.enabled) {
      return tr("chat.composer.uploadDisabled");
    }
    return "";
  });

  const ctx: ConversationStoreContext = {
    state: {
      conversations,
      conversationListAsOf,
      conversationListCursor,
      conversationListHasMore,
      conversationListLoading,
      searchDialogVisible,
      searchKeyword,
      searchResults,
      searchCursor,
      searchHasMore,
      searchLoading,
      searchSelectedIndex,
      anchorMessageUid,
      currentConversationUid,
      runningConversationUids,
      messages,
      messageHistoryCursor,
      messageHistoryHasMore,
      messageHistoryLoading,
      messageInitialLoaded,
      draftMessage,
      draftAttachments,
      uploadingFiles,
      loading,
      modelConfig,
      selectedModelProvider,
      selectedModelName,
      approvalMode,
      approval: approval as any,
      browserRuntimeOverlay: browserRuntimeOverlay as any,
      skipConversationListRefresh,
      streamingAssistantByParentUid,
      filteredConversations,
      currentConversation,
      currentConversationTitle,
      configuredProviders,
      availableModelOptions,
      hasAnyConfiguredModel,
      currentModelOption,
      selectedModelKey,
      currentUploadPolicy,
      uploadDisabledReason
    },
    deps: {
      agentCatalogStore,
      runtimeLogStore,
      conversationRunsStore,
      modelGateStore,
      conversationApi,
      fileApi,
      modelApi,
      subscribeConversationEvents,
      dialog,
      message,
      warningDialogPreset,
      tr,
      loadChatLastViewState,
      saveChatLastViewState,
      resolveApprovalFromPayload,
      resolveApprovalFromStep
    },
    internals: {
      eventSource: null,
      subscribedConversationUid: null,
      messageLoadToken: 0,
      conversationSummaryPollTimer: null,
      conversationSummaryPolling: false,
      conversationPageRequests: new Map(),
      conversationSearchRequests: new Map(),
      lastConversationPageLoadedAt: 0,
      lastConversationPageAgentUid: "",
      conversationPageLoadedOnce: false,
      searchRequestToken: 0,
      recentEventIds: [],
      recentEventIdSet: new Set()
    }
  };

  let runtimeModule!: ConversationRuntimeModule;
  let listModule!: ConversationListModule;
  let messagesModule!: ConversationMessagesModule;
  let eventsModule!: ConversationEventsModule;
  let composerModule!: ConversationComposerModule;

  runtimeModule = createConversationRuntimeModule(ctx);
  messagesModule = createConversationMessagesModule(ctx, {
    listModule: () => listModule,
    runtimeModule: () => runtimeModule,
    subscribeEvents: () => eventsModule.subscribeEvents(),
    disconnectEventSource: () => eventsModule.disconnectEventSource(),
    syncRuntimeModelSelection: (sourceMessages?: ConversationMessage[]) => composerModule.syncRuntimeModelSelection(sourceMessages)
  });
  listModule = createConversationListModule(ctx, {
    messagesModule: () => messagesModule,
    runtimeModule: () => runtimeModule,
    disconnectEventSource: () => eventsModule.disconnectEventSource(),
    syncRuntimeModelSelection: () => composerModule.syncRuntimeModelSelection(),
    loadModelConfig: () => composerModule.loadModelConfig()
  });
  eventsModule = createConversationEventsModule(ctx, {
    listModule: () => listModule,
    messagesModule: () => messagesModule,
    runtimeModule: () => runtimeModule
  });
  composerModule = createConversationComposerModule(ctx, {
    listModule: () => listModule,
    messagesModule: () => messagesModule,
    runtimeModule: () => runtimeModule,
    eventsModule: () => eventsModule
  });

  async function renameConversation(conversationUid: string, title: string) {
    await conversationApi.updateConversationTitle(conversationUid, title);
    await listModule.refreshConversations(conversationUid, true, true);
    if (conversationUid === currentConversationUid.value) {
      await messagesModule.refreshLatestMessages(conversationUid);
    }
  }

  async function updateConversationPin(conversationUid: string, pinned: boolean) {
    await conversationApi.updateConversationPin(conversationUid, pinned);
    await listModule.refreshConversations(conversationUid, true, true);
  }

  function openConversationSearch() {
    searchDialogVisible.value = true;
  }

  function closeConversationSearch() {
    searchDialogVisible.value = false;
    searchKeyword.value = "";
    searchResults.value = [];
    searchCursor.value = null;
    searchHasMore.value = false;
    searchLoading.value = false;
    searchSelectedIndex.value = 0;
  }

  async function openConversationSearchResult(result: ConversationSearchResult) {
    await messagesModule.selectConversationBySearch(result.conversationUid, searchKeyword.value);
    closeConversationSearch();
  }

  async function deleteConversation(conversationUid: string) {
    await conversationApi.deleteConversation(conversationUid);
    if (conversationUid === currentConversationUid.value) {
      messagesModule.startDraftConversation();
    }
    await listModule.refreshConversations(currentConversationUid.value, true, true);
  }

  function setSkipConversationListRefresh(value: boolean) {
    skipConversationListRefresh.value = value;
  }

  async function confirmDeleteConversation(conversationUid: string, title: string) {
    dialog.warning({
      title: tr("dialogs.deleteConversationTitle"),
      content: tr("dialogs.deleteConversationContent", { title: title || tr("chat.sidebar.unnamed") }),
      ...warningDialogPreset(),
      positiveText: tr("dialogs.confirmDelete"),
      negativeText: tr("common.cancel"),
      onPositiveClick: async () => {
        await deleteConversation(conversationUid);
        message.success(tr("toast.conversationDeleted"));
      }
    });
  }

  return {
    conversations,
    filteredConversations,
    currentConversationUid,
    currentConversation,
    currentConversationTitle,
    searchDialogVisible,
    searchKeyword,
    searchResults,
    searchCursor,
    searchHasMore,
    searchLoading,
    searchSelectedIndex,
    anchorMessageUid,
    runningConversationUids,
    messages,
    conversationListHasMore,
    conversationListLoading,
    messageHistoryHasMore,
    messageHistoryLoading,
    messageInitialLoaded,
    draftMessage,
    draftAttachments,
    uploadingFiles,
    loading,
    approval,
    browserRuntimeOverlay,
    modelConfig,
    availableModelOptions,
    currentModelOption,
    selectedModelProvider,
    selectedModelName,
    approvalMode,
    setApprovalMode: runtimeModule.setApprovalMode,
    selectedModelKey,
    currentUploadPolicy,
    uploadDisabledReason,
    hasAnyConfiguredModel,
    guideToModelSetup: composerModule.guideToModelSetup,
    init: listModule.init,
    loadModelConfig: composerModule.loadModelConfig,
    refreshConversations: listModule.refreshConversations,
    loadMoreConversations: listModule.loadMoreConversations,
    searchConversationHistory: listModule.searchConversationHistory,
    loadMoreSearchResults: listModule.loadMoreSearchResults,
    startDraftConversation: messagesModule.startDraftConversation,
    selectConversation: messagesModule.selectConversation,
    applyAgentSelection: messagesModule.applyAgentSelection,
    changeRuntimeModel: composerModule.changeRuntimeModel,
    uploadFiles: composerModule.uploadFiles,
    removeDraftAttachment: composerModule.removeDraftAttachment,
    sendMessage: composerModule.sendMessage,
    cancelRunningMessage: composerModule.cancelRunningMessage,
    approveStep: composerModule.approveStep,
    rejectStep: composerModule.rejectStep,
    loadOlderMessages: messagesModule.loadOlderMessages,
    setSkipConversationListRefresh,
    openFile: messagesModule.openFile,
    renameConversation,
    updateConversationPin,
    openConversationSearch,
    closeConversationSearch,
    openConversationSearchResult,
    confirmDeleteConversation
  };
});
