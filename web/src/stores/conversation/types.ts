import type { ComputedRef, Ref } from "vue";
import type { conversationApi } from "@/api/conversationApi";
import type { fileApi } from "@/api/fileApi";
import type { subscribeConversationEvents } from "@/api/eventStreamApi";
import type { modelApi } from "@/api/modelApi";
import type { dialog, message, warningDialogPreset } from "@/discrete";
import type { useAgentCatalogStore } from "@/stores/agentCatalog";
import type { loadChatLastViewState, saveChatLastViewState } from "@/stores/chatViewState";
import type { useConversationRunsStore } from "@/stores/conversationRuns";
import type { useModelGateStore } from "@/stores/modelGate";
import type { useRuntimeLogStore } from "@/stores/runtimeLog";
import type { resolveApprovalFromPayload, resolveApprovalFromStep } from "@/utils/approvalRenderer";
import type { ApprovalLabelKey } from "@/utils/approvalRenderer";
import type {
  ApprovalMode,
  AgentEvent,
  ConversationAttachment,
  ConversationMessage,
  ConversationMessageRun,
  ConversationRunStep,
  ConversationSummary,
  ConversationSummaryPage,
  ModelConfig,
  ModelProviderOption,
  UploadPolicy
} from "@/types/api";

export interface ApprovalState {
  stepUid: string | null;
  title: string;
  body: string;
  labelKey: ApprovalLabelKey;
  command: string;
  toolName: string;
  policyReasonCode: string;
  riskLevel: string;
  submitting: boolean;
  submittingAction: "allow_once" | "allow_session" | "allow_agent" | "allow_user" | "deny_once" | null;
}

export interface BrowserRuntimeOverlayState {
  visible: boolean;
  stepUid: string | null;
  attemptId: string;
  attemptIndex: number;
  retryCount: number;
  title: string;
  details: string;
  hint: string;
  downloadedBytes: number;
  progressPercent: number;
  indeterminate: boolean;
  currentArtifact: string;
  segmentPercent: number;
  overallPercent: number;
  completedArtifacts: string[];
}

export interface ConversationStoreStateRefs {
  conversations: Ref<ConversationSummary[]>;
  conversationListAsOf: Ref<string>;
  conversationListCursor: Ref<string | null>;
  conversationListHasMore: Ref<boolean>;
  conversationListLoading: Ref<boolean>;
  currentConversationUid: Ref<string | null>;
  runningConversationUid: Ref<string | null>;
  messages: Ref<ConversationMessage[]>;
  messageHistoryCursor: Ref<string | null>;
  messageHistoryHasMore: Ref<boolean>;
  messageHistoryLoading: Ref<boolean>;
  messageInitialLoaded: Ref<boolean>;
  draftMessage: Ref<string>;
  draftAttachments: Ref<ConversationAttachment[]>;
  uploadingFiles: Ref<boolean>;
  loading: Ref<boolean>;
  modelConfig: Ref<ModelConfig>;
  selectedModelProvider: Ref<string>;
  selectedModelName: Ref<string>;
  approvalMode: Ref<ApprovalMode>;
  approval: Ref<ApprovalState>;
  browserRuntimeOverlay: Ref<BrowserRuntimeOverlayState>;
  skipConversationListRefresh: Ref<boolean>;
  streamingAssistantByParentUid: Ref<Record<string, number>>;
  filteredConversations: ComputedRef<ConversationSummary[]>;
  currentConversation: ComputedRef<ConversationSummary | null>;
  currentConversationTitle: ComputedRef<string>;
  configuredProviders: ComputedRef<any[]>;
  availableModelOptions: ComputedRef<any[]>;
  hasAnyConfiguredModel: ComputedRef<boolean>;
  currentModelOption: ComputedRef<ModelProviderOption | null>;
  selectedModelKey: ComputedRef<string>;
  currentUploadPolicy: ComputedRef<UploadPolicy>;
  uploadDisabledReason: ComputedRef<string>;
}

export interface ConversationStoreInternals {
  eventSource: EventSource | null;
  subscribedConversationUid: string | null;
  messageLoadToken: number;
  conversationSummaryPollTimer: number | null;
  conversationSummaryPolling: boolean;
  conversationPageRequests: Map<string, Promise<ConversationSummaryPage>>;
  lastConversationPageLoadedAt: number;
  lastConversationPageAgentUid: string;
  conversationPageLoadedOnce: boolean;
  recentEventIds: string[];
  recentEventIdSet: Set<string>;
}

export interface ConversationStoreDependencies {
  agentCatalogStore: ReturnType<typeof useAgentCatalogStore>;
  runtimeLogStore: ReturnType<typeof useRuntimeLogStore>;
  conversationRunsStore: ReturnType<typeof useConversationRunsStore>;
  modelGateStore: ReturnType<typeof useModelGateStore>;
  conversationApi: typeof conversationApi;
  fileApi: typeof fileApi;
  modelApi: typeof modelApi;
  subscribeConversationEvents: typeof subscribeConversationEvents;
  dialog: typeof dialog;
  message: typeof message;
  warningDialogPreset: typeof warningDialogPreset;
  tr: typeof import("@/i18n").tr;
  loadChatLastViewState: typeof loadChatLastViewState;
  saveChatLastViewState: typeof saveChatLastViewState;
  resolveApprovalFromPayload: typeof resolveApprovalFromPayload;
  resolveApprovalFromStep: typeof resolveApprovalFromStep;
}

export interface ConversationStoreContext {
  state: ConversationStoreStateRefs;
  deps: ConversationStoreDependencies;
  internals: ConversationStoreInternals;
}

export interface ConversationListModule {
  stopConversationSummaryPolling: () => void;
  scheduleConversationSummaryPolling: () => void;
  loadConversationSummaries: (force?: boolean) => Promise<void>;
  loadMoreConversations: () => Promise<void>;
  refreshConversations: (
    preferredConversationUid?: string | null,
    withLoading?: boolean,
    force?: boolean
  ) => Promise<void>;
  init: () => Promise<void>;
  patchConversationSummaryLocally: (conversationUid: string, patch: Partial<ConversationSummary>) => void;
  finalizeActiveConversationSummary: (conversationUid: string) => void;
}

export interface ConversationMessagesModule {
  startDraftConversation: () => void;
  selectConversation: (conversationUid: string) => Promise<void>;
  loadOlderMessages: () => Promise<void>;
  refreshLatestMessages: (conversationUid: string) => Promise<void>;
  applyAgentSelection: () => Promise<void>;
  markConversationReadLocally: (conversationUid: string) => void;
  keepCurrentConversationRead: (conversationUid: string) => Promise<void>;
  reconcileRunningConversationByMessages: (conversationUid: string, loadedMessages: ConversationMessage[]) => void;
  resetMessageState: () => void;
  openFile: (path: string) => Promise<void>;
}

export interface ConversationRuntimeModule {
  setApprovalMode: (mode: ApprovalMode) => void;
  clearApproval: () => void;
  clearBrowserRuntimeOverlay: () => void;
  restoreApprovalModeForConversation: (conversationUid: string | null) => void;
  restoreApprovalFromRuns: (runs: ConversationMessageRun[]) => void;
  updateBrowserRuntimeOverlayFromStepEvent: (event: AgentEvent) => void;
  clearStreamingAssistantDraft: (parentMessageUid?: string) => void;
  upsertStreamingAssistantDelta: (
    parentMessageUid: string,
    textDelta: string,
    createdTime?: string,
    accumulatedText?: string,
    done?: boolean
  ) => void;
  resetRuntimePanels: () => void;
  handlePlanCreated: (event: AgentEvent) => void;
  handleRunStepEvent: (event: AgentEvent) => void;
  handleReasoningEvent: (event: AgentEvent) => void;
  showApprovalAlert: (event: AgentEvent) => void;
  renderPlanSteps: (steps: Record<string, any>[]) => void;
}

export interface ConversationComposerModule {
  loadModelConfig: () => Promise<void>;
  syncRuntimeModelSelection: (sourceMessages?: ConversationMessage[]) => void;
  hasConfiguredModel: (modelProvider: string, modelName: string) => boolean;
  guideToModelSetup: () => void;
  changeRuntimeModel: (value: string) => Promise<void>;
  uploadFiles: (fileList: FileList | File[]) => Promise<void>;
  removeDraftAttachment: (fileUrl: string) => void;
  sendMessage: () => Promise<void>;
  cancelRunningMessage: () => Promise<void>;
  approveStep: (scope?: "once" | "session" | "agent" | "user") => Promise<void>;
  rejectStep: () => Promise<void>;
}

export interface ConversationEventsModule {
  subscribeEvents: () => void;
  disconnectEventSource: () => void;
  handleEvent: (event: AgentEvent) => Promise<void>;
}
