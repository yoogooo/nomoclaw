import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { conversationApi } from "@/api/conversationApi";
import { fileApi } from "@/api/fileApi";
import { subscribeConversationEvents } from "@/api/eventStreamApi";
import { modelApi } from "@/api/modelApi";
import { dialog, message, warningDialogPreset } from "@/discrete";
import { tr } from "@/i18n";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationRunsStore } from "@/stores/conversationRuns";
import { useModelGateStore } from "@/stores/modelGate";
import { useRuntimeLogStore } from "@/stores/runtimeLog";
import type {
  AgentEvent,
  ConversationAttachment,
  ConversationMessage,
  ConversationMessageRun,
  ConversationRunStep,
  ConversationSummary,
  ModelConfig,
  ModelProviderOption,
  UploadPolicy
} from "@/types/api";
import { approvalActionSummary } from "@/utils/format";

interface ApprovalState {
  stepUid: string | null;
  title: string;
  body: string;
  riskLevel: string;
  submitting: boolean;
  submittingAction: "approve" | "reject" | null;
}

interface BrowserRuntimeOverlayState {
  visible: boolean;
  stepUid: string | null;
  title: string;
  details: string;
  hint: string;
  downloadedBytes: number;
  progressPercent: number;
}

const EMPTY_UPLOAD_POLICY: UploadPolicy = {
  enabled: false,
  allowedMimeGroups: [],
  maxFilesPerMessage: 0,
  maxImagesPerMessage: 0,
  maxFileBytes: 0,
  maxTotalBytes: 0,
  singleMimeGroupOnly: false,
  allowMixedImageAndFile: false
};

function buildModelKey(modelProvider: string, modelName: string) {
  return `${modelProvider}::${modelName}`;
}

function splitModelKey(value: string) {
  const [modelProvider = "", modelName = ""] = value.split("::");
  return { modelProvider, modelName };
}

function normalizeUploadPolicy(policy?: UploadPolicy | null): UploadPolicy {
  if (!policy) {
    return { ...EMPTY_UPLOAD_POLICY };
  }
  return {
    enabled: Boolean(policy.enabled),
    allowedMimeGroups: Array.isArray(policy.allowedMimeGroups) ? policy.allowedMimeGroups.filter(Boolean) : [],
    maxFilesPerMessage: Math.max(0, Number(policy.maxFilesPerMessage || 0)),
    maxImagesPerMessage: Math.max(0, Number(policy.maxImagesPerMessage || 0)),
    maxFileBytes: Math.max(0, Number(policy.maxFileBytes || 0)),
    maxTotalBytes: Math.max(0, Number(policy.maxTotalBytes || 0)),
    singleMimeGroupOnly: Boolean(policy.singleMimeGroupOnly),
    allowMixedImageAndFile: Boolean(policy.allowMixedImageAndFile)
  };
}

function isProviderConfigured(provider: ModelConfig["providers"][number]) {
  if (provider.local) {
    return Boolean(provider.baseUrl?.trim());
  }
  return Boolean(provider.apiKey?.trim());
}

function normalizeMimeGroupFromName(fileName: string) {
  const normalized = fileName.toLowerCase();
  if (normalized.endsWith(".pdf")) {
    return "pdf";
  }
  if ([".txt", ".md", ".json", ".csv", ".yaml", ".yml", ".xml"].some((suffix) => normalized.endsWith(suffix))) {
    return "text";
  }
  return "application";
}

function resolveMimeGroup(file: File) {
  const contentType = (file.type || "").toLowerCase();
  if (contentType.startsWith("image/")) {
    return "image";
  }
  if (contentType.startsWith("audio/")) {
    return "audio";
  }
  if (contentType.startsWith("video/")) {
    return "video";
  }
  if (contentType === "application/pdf") {
    return "pdf";
  }
  if (contentType.startsWith("text/") || ["application/json", "application/xml", "application/yaml", "application/x-yaml", "application/csv"].includes(contentType)) {
    return "text";
  }
  return normalizeMimeGroupFromName(file.name || "");
}

export const useConversationStore = defineStore("conversation", () => {
  const agentCatalogStore = useAgentCatalogStore();
  const runtimeLogStore = useRuntimeLogStore();
  const conversationRunsStore = useConversationRunsStore();
  const modelGateStore = useModelGateStore();

  const conversations = ref<ConversationSummary[]>([]);
  const currentConversationUid = ref<string | null>(null);
  const runningConversationUid = ref<string | null>(null);
  const messages = ref<ConversationMessage[]>([]);
  const draftMessage = ref("");
  const draftAttachments = ref<ConversationAttachment[]>([]);
  const uploadingFiles = ref(false);
  const loading = ref(false);
  const modelConfig = ref<ModelConfig>({ providers: [] });
  const selectedModelProvider = ref("");
  const selectedModelName = ref("");
  const approval = ref<ApprovalState>({
    stepUid: null,
    title: "",
    body: "",
    riskLevel: "HIGH",
    submitting: false,
    submittingAction: null
  });
  const browserRuntimeOverlay = ref<BrowserRuntimeOverlayState>({
    visible: false,
    stepUid: null,
    title: "",
    details: "",
    hint: "",
    downloadedBytes: 0,
    progressPercent: 0
  });

  let eventSource: EventSource | null = null;
  const streamingAssistantByParentUid = ref<Record<string, number>>({});

  const filteredConversations = computed(() =>
    conversations.value.filter((item) => agentCatalogStore.matchesConversation(item))
  );

  const currentConversation = computed(() =>
    conversations.value.find((item) => item.conversationUid === currentConversationUid.value) || null
  );

  const currentConversationTitle = computed(() => currentConversation.value?.title || "Conversation");

  const configuredProviders = computed(() =>
    modelConfig.value.providers
      .filter((provider) => isProviderConfigured(provider))
      .map((provider) => ({
        ...provider,
        models: provider.models.filter((model) => Boolean(model.id?.trim()))
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

  function hasConfiguredModel(modelProvider: string, modelName: string) {
    if (!modelProvider || !modelName) {
      return false;
    }
    const provider = configuredProviders.value.find((item) => item.id === modelProvider);
    if (!provider) {
      return false;
    }
    return provider.models.some((model) => model.id === modelName);
  }

  function promptModelSetupGuide() {
    modelGateStore.resetPrompt();
    if (!modelGateStore.checking) {
      void modelGateStore.refreshModelReadiness();
    }
  }

  function guideToModelSetup() {
    promptModelSetupGuide();
    message.warning(tr("modelGate.status.missingModel"));
  }

  function clearApproval() {
    approval.value = {
      stepUid: null,
      title: "",
      body: "",
      riskLevel: "HIGH",
      submitting: false,
      submittingAction: null
    };
  }

  function clearBrowserRuntimeOverlay() {
    browserRuntimeOverlay.value = {
      visible: false,
      stepUid: null,
      title: "",
      details: "",
      hint: "",
      downloadedBytes: 0,
      progressPercent: 0
    };
  }

  function updateBrowserRuntimeOverlayFromStepEvent(event: AgentEvent) {
    const toolName = String(event.payload.toolName || "");
    if (toolName !== "browser_tool" && toolName !== "browser_control_tool") {
      return;
    }
    const metrics = (event.payload.progressMetrics || {}) as Record<string, any>;
    const phase = String(metrics.phase || "");
    const downloadedBytesRaw = Number(metrics.downloadedBytes ?? metrics.cachedBytes ?? 0);
    const downloadedBytes = Number.isFinite(downloadedBytesRaw)
      ? Math.max(0, Math.round(downloadedBytesRaw))
      : 0;
    const progressPercentRaw = Number(metrics.progressPercent || 0);
    const progressPercent = Number.isFinite(progressPercentRaw)
      ? Math.max(0, Math.min(100, Math.round(progressPercentRaw)))
      : 0;
    const title = phase === "checking"
      ? tr("chat.runtime.browserRuntime.checkingTitle")
      : tr("chat.runtime.browserRuntime.downloadingTitle");
    const details = phase === "checking"
      ? tr("chat.runtime.browserRuntime.checkingDesc")
      : tr("chat.runtime.browserRuntime.downloadingDescSimple");
    const hint = tr("chat.runtime.browserRuntime.oneTimeHint");

    if (phase === "checking" || phase === "downloading") {
      browserRuntimeOverlay.value = {
        visible: true,
        stepUid: event.stepUid || null,
        title,
        details,
        hint,
        downloadedBytes: Math.max(0, downloadedBytes),
        progressPercent
      };
      return;
    }
    if (phase === "ready" && browserRuntimeOverlay.value.stepUid === (event.stepUid || null)) {
      clearBrowserRuntimeOverlay();
    }
  }

  function buildApprovalBodyFromStep(step: ConversationRunStep) {
    const details = (step.displayDetails || step.displaySummary || "").trim();
    const main = details || tr("chat.runtime.stepNeedApproval", { index: step.stepIndex || "-" });
    return `${main}\n\n${tr("chat.runtime.approvalRiskHint")}`;
  }

  function restoreApprovalFromRuns(runs: ConversationMessageRun[]) {
    const waitingSteps = runs
      .flatMap((run) =>
        (run.steps || [])
          .filter((step) => step.status === "waiting_approval")
          .map((step) => ({ run, step }))
      )
      .sort((left, right) => {
        const leftTime = Date.parse(left.step.updatedTime || left.run.updatedTime || "");
        const rightTime = Date.parse(right.step.updatedTime || right.run.updatedTime || "");
        if (!Number.isNaN(leftTime) && !Number.isNaN(rightTime) && leftTime !== rightTime) {
          return rightTime - leftTime;
        }
        if (left.step.roundIndex !== right.step.roundIndex) {
          return right.step.roundIndex - left.step.roundIndex;
        }
        return right.step.stepIndex - left.step.stepIndex;
      });

    const current = waitingSteps[0];
    if (!current) {
      clearApproval();
      return;
    }

    approval.value = {
      stepUid: current.step.stepUid || null,
      title: current.step.displayTitle || tr("chat.runtime.highRiskStep"),
      body: buildApprovalBodyFromStep(current.step),
      riskLevel: "HIGH",
      submitting: approval.value.stepUid === current.step.stepUid ? approval.value.submitting : false,
      submittingAction: approval.value.stepUid === current.step.stepUid ? approval.value.submittingAction : null
    };
  }

  function disconnectEventSource() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
  }

  function clearStreamingAssistantDraft(parentMessageUid?: string) {
    if (!parentMessageUid) {
      const indexes = Object.values(streamingAssistantByParentUid.value);
      if (!indexes.length) return;
      const indexSet = new Set(indexes);
      messages.value = messages.value.filter((_, index) => !indexSet.has(index));
      streamingAssistantByParentUid.value = {};
      return;
    }
    const index = streamingAssistantByParentUid.value[parentMessageUid];
    if (index === undefined) return;
    messages.value = messages.value.filter((_, i) => i !== index);
    const nextMap: Record<string, number> = {};
    for (const [key, value] of Object.entries(streamingAssistantByParentUid.value)) {
      if (key === parentMessageUid) continue;
      nextMap[key] = value > index ? value - 1 : value;
    }
    streamingAssistantByParentUid.value = nextMap;
  }

  function upsertStreamingAssistantDelta(parentMessageUid: string, textDelta: string, createdTime?: string, accumulatedText?: string, done?: boolean) {
    if (!parentMessageUid) return;
    const existingIndex = streamingAssistantByParentUid.value[parentMessageUid];
    if (existingIndex !== undefined && messages.value[existingIndex]) {
      const existing = messages.value[existingIndex];
      messages.value[existingIndex] = {
        ...existing,
        content: done && accumulatedText !== undefined ? accumulatedText : `${existing.content || ""}${textDelta}`,
        status: done ? "COMPLETED" : "RUNNING"
      };
      return;
    }
    if (!textDelta && accumulatedText === undefined) return;
    const assistantDraft: ConversationMessage = {
      role: "assistant",
      parentMessageUid,
      content: accumulatedText ?? textDelta,
      status: done ? "COMPLETED" : "RUNNING",
      createdTime: createdTime || new Date().toISOString(),
      fileLinks: [],
      attachments: []
    };
    messages.value = [...messages.value, assistantDraft];
    streamingAssistantByParentUid.value = {
      ...streamingAssistantByParentUid.value,
      [parentMessageUid]: messages.value.length - 1
    };
  }

  function resetRuntimePanels() {
    runtimeLogStore.clear();
    conversationRunsStore.clear();
    clearApproval();
    clearBrowserRuntimeOverlay();
    clearStreamingAssistantDraft();
  }

  function findAgentDefaultModel() {
    const agent = agentCatalogStore.allAgents.find((item) => item.agentUid === agentCatalogStore.selectedAgentUid);
    const matchedAgentProvider = configuredProviders.value.find((provider) => provider.id === agent?.modelProvider);
    if (agent?.modelProvider && agent?.modelName && matchedAgentProvider?.models.some((model) => model.id === agent.modelName)) {
      return { modelProvider: agent.modelProvider, modelName: agent.modelName };
    }
    const firstProvider = configuredProviders.value.find((provider) => provider.models.length);
    const firstModel = firstProvider?.models[0];
    return {
      modelProvider: firstProvider?.id || "",
      modelName: firstModel?.id || ""
    };
  }

  function findLatestMessageModelSelection(sourceMessages: ConversationMessage[] = messages.value) {
    const latestUserMessage = [...sourceMessages]
      .reverse()
      .find((item) => item.role === "user" && item.provider && item.modelName);
    const latestProvider = latestUserMessage
      ? configuredProviders.value.find((provider) => provider.id === latestUserMessage.provider)
      : null;
    if (latestUserMessage?.provider && latestUserMessage?.modelName && latestProvider?.models.some((model) => model.id === latestUserMessage.modelName)) {
      return { modelProvider: latestUserMessage.provider, modelName: latestUserMessage.modelName };
    }
    return null;
  }

  function syncRuntimeModelSelection() {
    const latestMessageModel = findLatestMessageModelSelection();
    if (latestMessageModel) {
      selectedModelProvider.value = latestMessageModel.modelProvider;
      selectedModelName.value = latestMessageModel.modelName;
      return;
    }
    const fallback = findAgentDefaultModel();
    selectedModelProvider.value = fallback.modelProvider;
    selectedModelName.value = fallback.modelName;
  }

  function isInProgressMessageStatus(status?: string) {
    const normalized = (status || "").trim().toUpperCase();
    if (!normalized) {
      return false;
    }
    return !["COMPLETED", "FAILED", "CANCELED"].includes(normalized);
  }

  function reconcileRunningConversationByMessages(conversationUid: string, loadedMessages: ConversationMessage[]) {
    const latestUserMessage = [...loadedMessages]
      .reverse()
      .find((item) => item.role === "user" && item.messageUid);
    const isRunning = isInProgressMessageStatus(latestUserMessage?.status);
    if (isRunning) {
      runningConversationUid.value = conversationUid;
      return;
    }
    if (runningConversationUid.value === conversationUid) {
      runningConversationUid.value = null;
    }
  }

  async function loadModelConfig() {
    modelConfig.value = await modelApi.getAvailableModelConfig();
    syncRuntimeModelSelection();
  }

  async function loadMessages(conversationUid: string) {
    const [loadedMessages, runs] = await Promise.all([
      conversationApi.listMessages(conversationUid),
      conversationApi.listMessageRuns(conversationUid)
    ]);
    messages.value = loadedMessages;
    reconcileRunningConversationByMessages(conversationUid, loadedMessages);
    conversationRunsStore.setRuns(runs);
    restoreApprovalFromRuns(runs);
  }

  function subscribeEvents() {
    if (!currentConversationUid.value) return;
    disconnectEventSource();
    eventSource = subscribeConversationEvents(currentConversationUid.value, handleEvent);
  }

  function validateFilesAgainstPolicy(policy: UploadPolicy, existing: ConversationAttachment[], files: File[]) {
    if (!policy.enabled) {
      throw new Error(tr("chat.composer.uploadDisabled"));
    }
    const combinedGroups = new Set<string>([
      ...existing.map((item) => item.mimeGroup),
      ...files.map((file) => resolveMimeGroup(file))
    ]);
    if (!combinedGroups.size) {
      return;
    }
    if (policy.singleMimeGroupOnly && combinedGroups.size > 1) {
      throw new Error(tr("chat.composer.singleTypeOnly"));
    }
    const unsupportedGroup = [...combinedGroups].find((group) =>
      policy.allowedMimeGroups.length && !policy.allowedMimeGroups.includes(group)
    );
    if (unsupportedGroup) {
      throw new Error(tr("chat.composer.unsupportedType"));
    }

    const existingImageCount = existing.filter((item) => item.mimeGroup === "image").length;
    const incomingImageCount = files.filter((file) => resolveMimeGroup(file) === "image").length;
    const imageCount = existingImageCount + incomingImageCount;
    const totalCount = existing.length + files.length;
    const nonImageCount = totalCount - imageCount;
    const maxFileBytes = policy.maxFileBytes || 0;
    if (maxFileBytes > 0 && files.some((file) => file.size > maxFileBytes)) {
      throw new Error(tr("chat.composer.maxFileSize", { size: formatBytes(maxFileBytes) }));
    }
    const totalBytes = existing.reduce((sum, item) => sum + item.sizeBytes, 0) + files.reduce((sum, file) => sum + file.size, 0);
    const maxTotalBytes = policy.maxTotalBytes || 0;
    if (maxTotalBytes > 0 && totalBytes > maxTotalBytes) {
      throw new Error(tr("chat.composer.maxTotalSize", { size: formatBytes(maxTotalBytes) }));
    }

    if (imageCount > 0 && nonImageCount > 0 && !policy.allowMixedImageAndFile) {
      throw new Error(tr("chat.composer.mixedTypeNotAllowed"));
    }
    if (imageCount > 0 && imageCount > policy.maxImagesPerMessage) {
      throw new Error(tr("chat.composer.maxImages", { count: policy.maxImagesPerMessage }));
    }
    if (nonImageCount > 0 && nonImageCount > policy.maxFilesPerMessage) {
      throw new Error(tr("chat.composer.maxFiles", { count: policy.maxFilesPerMessage }));
    }
  }

  function formatBytes(bytes: number) {
    const mb = Math.floor(bytes / (1024 * 1024));
    return mb > 0 ? `${mb}MB` : `${bytes}B`;
  }

  function clearDraftAttachments(reason?: string) {
    draftAttachments.value = [];
    if (reason) {
      message.warning(reason);
    }
  }

  async function refreshConversations(preferredConversationUid: string | null = currentConversationUid.value) {
    loading.value = true;
    try {
      conversations.value = await conversationApi.listConversations();
      agentCatalogStore.ensureSelection();

      if (!filteredConversations.value.length) {
        currentConversationUid.value = null;
        runningConversationUid.value = null;
        messages.value = [];
        resetRuntimePanels();
        disconnectEventSource();
        syncRuntimeModelSelection();
        return;
      }

      const nextConversationUid = filteredConversations.value.some((item) => item.conversationUid === preferredConversationUid)
        ? preferredConversationUid
        : filteredConversations.value[0].conversationUid;

      if (nextConversationUid && nextConversationUid !== currentConversationUid.value) {
        await selectConversation(nextConversationUid);
        return;
      }

      syncRuntimeModelSelection();
    } finally {
      loading.value = false;
    }
  }

  async function init() {
    agentCatalogStore.restoreSelection();
    await Promise.all([agentCatalogStore.loadCatalog(), loadModelConfig()]);
    await refreshConversations();
    if (!currentConversationUid.value) {
      startDraftConversation();
    }
  }

  function startDraftConversation() {
    const previousConversationModel = findLatestMessageModelSelection();
    currentConversationUid.value = null;
    runningConversationUid.value = null;
    messages.value = [];
    draftMessage.value = "";
    draftAttachments.value = [];
    resetRuntimePanels();
    disconnectEventSource();
    if (previousConversationModel) {
      selectedModelProvider.value = previousConversationModel.modelProvider;
      selectedModelName.value = previousConversationModel.modelName;
      return;
    }
    syncRuntimeModelSelection();
  }

  async function selectConversation(conversationUid: string) {
    currentConversationUid.value = conversationUid;
    draftAttachments.value = [];
    resetRuntimePanels();
    await loadMessages(conversationUid);
    syncRuntimeModelSelection();
    subscribeEvents();
  }

  async function applyAgentSelection() {
    await refreshConversations();
    if (!filteredConversations.value.length) {
      startDraftConversation();
      return;
    }
    syncRuntimeModelSelection();
  }

  async function renameConversation(conversationUid: string, title: string) {
    await conversationApi.updateConversationTitle(conversationUid, title);
    await refreshConversations(conversationUid);
    if (conversationUid === currentConversationUid.value) {
      await loadMessages(conversationUid);
    }
  }

  async function deleteConversation(conversationUid: string) {
    await conversationApi.deleteConversation(conversationUid);
    if (conversationUid === currentConversationUid.value) {
      startDraftConversation();
    }
    await refreshConversations();
  }

  async function ensureConversationForInteraction() {
    if (currentConversationUid.value) {
      return currentConversationUid.value;
    }

    const createGroupUid = "";
    const createAgentUid = agentCatalogStore.selectedAgentUid;
    const created = await conversationApi.createConversation(createGroupUid, createAgentUid);
    currentConversationUid.value = created.conversationUid;
    await refreshConversations(created.conversationUid);
    subscribeEvents();
    return created.conversationUid;
  }

  async function changeRuntimeModel(value: string) {
    const { modelProvider, modelName } = splitModelKey(value);
    if (!modelProvider || !modelName) {
      return;
    }
    if (modelProvider === selectedModelProvider.value && modelName === selectedModelName.value) {
      return;
    }
    const nextOption = configuredProviders.value
      .find((provider) => provider.id === modelProvider)
      ?.models.find((model) => model.id === modelName);
    const nextPolicy = normalizeUploadPolicy(nextOption?.uploadPolicy);
    if (draftAttachments.value.length) {
      const existingFits = (() => {
        try {
          validateFilesAgainstPolicy(nextPolicy, draftAttachments.value, []);
          return true;
        } catch {
          return false;
        }
      })();
      if (!existingFits) {
        clearDraftAttachments(tr("chat.composer.clearedByModelSwitch"));
      }
    }
    selectedModelProvider.value = modelProvider;
    selectedModelName.value = modelName;
  }

  async function uploadFiles(fileList: FileList | File[]) {
    const files = Array.from(fileList || []).filter(Boolean);
    if (!files.length) {
      return;
    }
    if (!hasAnyConfiguredModel.value) {
      guideToModelSetup();
      return;
    }
    try {
      validateFilesAgainstPolicy(currentUploadPolicy.value, draftAttachments.value, files);
    } catch (error) {
      message.error(error instanceof Error ? error.message : tr("toast.uploadRuleNotMatch"));
      return;
    }
    const lockedProvider = selectedModelProvider.value;
    const lockedModelName = selectedModelName.value;
    const conversationUid = await ensureConversationForInteraction();
    const effectiveProvider = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedProvider : selectedModelProvider.value;
    const effectiveModelName = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedModelName : selectedModelName.value;
    if (effectiveProvider && effectiveModelName) {
      selectedModelProvider.value = effectiveProvider;
      selectedModelName.value = effectiveModelName;
    }
    uploadingFiles.value = true;
    try {
      const uploaded = await conversationApi.uploadConversationFiles(conversationUid, {
        files,
        modelProvider: effectiveProvider,
        modelName: effectiveModelName
      });
      draftAttachments.value = [...draftAttachments.value, ...uploaded.items];
    } finally {
      uploadingFiles.value = false;
    }
  }

  function removeDraftAttachment(fileUrl: string) {
    draftAttachments.value = draftAttachments.value.filter((item) => item.fileUrl !== fileUrl);
  }

  async function sendMessage() {
    if (currentConversationUid.value && runningConversationUid.value === currentConversationUid.value) {
      await cancelRunningMessage();
      return;
    }

    const content = draftMessage.value.trim();
    if (!content) {
      return;
    }
    if (!selectedModelProvider.value || !selectedModelName.value) {
      if (!hasAnyConfiguredModel.value) {
        guideToModelSetup();
        return;
      }
      message.error(tr("toast.chooseModelFirst"));
      return;
    }

    const lockedProvider = selectedModelProvider.value;
    const lockedModelName = selectedModelName.value;
    const conversationUid = await ensureConversationForInteraction();
    const effectiveProvider = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedProvider : selectedModelProvider.value;
    const effectiveModelName = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedModelName : selectedModelName.value;
    if (!effectiveProvider || !effectiveModelName) {
      if (!hasAnyConfiguredModel.value) {
        guideToModelSetup();
        return;
      }
      message.error(tr("toast.chooseModelFirst"));
      return;
    }
    selectedModelProvider.value = effectiveProvider;
    selectedModelName.value = effectiveModelName;
    const attachments = [...draftAttachments.value];
    const tempMessage: ConversationMessage = {
      role: "user",
      content,
      status: "CREATED",
      provider: effectiveProvider,
      modelName: effectiveModelName,
      createdTime: new Date().toISOString(),
      attachments
    };

    messages.value = [...messages.value, tempMessage];
    draftMessage.value = "";
    draftAttachments.value = [];

    try {
      const accepted = await conversationApi.sendMessage(conversationUid, {
        message: content,
        fileUrls: attachments.map((item) => item.fileUrl),
        modelProvider: effectiveProvider,
        modelName: effectiveModelName
      });
      tempMessage.messageUid = accepted.messageUid;
      runningConversationUid.value = conversationUid;
      runtimeLogStore.append(tr("chat.runtime.messageSubmitted", { messageUid: accepted.messageUid }));
      await refreshConversations(conversationUid);
    } catch (error) {
      messages.value = messages.value.filter((item) => item !== tempMessage);
      draftMessage.value = content;
      draftAttachments.value = attachments;
      throw error;
    }
  }

  async function cancelRunningMessage() {
    if (!currentConversationUid.value) return;
    await conversationApi.cancelConversation(currentConversationUid.value);
    runningConversationUid.value = null;
  }

  async function approveStep() {
    if (!currentConversationUid.value || !approval.value.stepUid || approval.value.submitting) return;
    const stepUid = approval.value.stepUid;
    approval.value = {
      ...approval.value,
      submitting: true,
      submittingAction: "approve"
    };
    try {
      await conversationApi.approveStep(currentConversationUid.value, stepUid);
      message.success(tr("toast.approveSuccess"));
      runtimeLogStore.append(tr("chat.runtime.approvalSubmitted", { action: "approve", stepUid }));
    } catch (error) {
      approval.value = {
        ...approval.value,
        submitting: false,
        submittingAction: null
      };
      message.error(tr("toast.approveFailed"));
      throw error;
    }
  }

  async function rejectStep() {
    if (!currentConversationUid.value || !approval.value.stepUid || approval.value.submitting) return;
    const stepUid = approval.value.stepUid;
    approval.value = {
      ...approval.value,
      submitting: true,
      submittingAction: "reject"
    };
    try {
      await conversationApi.rejectStep(currentConversationUid.value, stepUid);
      message.warning(tr("toast.rejectSuccess"));
      runtimeLogStore.append(tr("chat.runtime.approvalSubmitted", { action: "reject", stepUid }));
    } catch (error) {
      approval.value = {
        ...approval.value,
        submitting: false,
        submittingAction: null
      };
      message.error(tr("toast.rejectFailed"));
      throw error;
    }
  }

  async function openFile(path: string) {
    await fileApi.openFile(path);
  }

  function handlePlanCreated(event: AgentEvent) {
    const displaySteps = event.payload.displaySteps || [];
    if (!event.messageUid) return;
    const existingRun = conversationRunsStore.runsByMessageUid[event.messageUid];
    const stepsByUid = new Map<string, ConversationRunStep>();

    (existingRun?.steps || []).forEach((step) => {
      if (!step.stepUid) return;
      stepsByUid.set(step.stepUid, step);
    });

    displaySteps.forEach((step: Record<string, any>) => {
      const stepUid = step.stepUid || "";
      if (!stepUid) return;
      const normalized: ConversationRunStep = {
        stepUid,
        roundIndex: Number(step.roundIndex || 1),
        stepIndex: Number(step.stepIndex || 1),
        status: step.status || "planned",
        displayTitle: step.displayTitle || tr("chat.runtime.processingStep"),
        displaySummary: step.displaySummary || "",
        displayDetails: step.displayDetails || "",
        updatedTime: step.updatedTime || new Date().toISOString()
      };
      const previous = stepsByUid.get(stepUid);
      stepsByUid.set(stepUid, previous ? { ...previous, ...normalized } : normalized);
    });

    const mergedSteps = [...stepsByUid.values()];

    conversationRunsStore.upsertRun({
      messageUid: event.messageUid,
      status: existingRun?.status || "planned",
      summary: existingRun?.summary || "",
      completedSteps: existingRun?.completedSteps || 0,
      totalSteps: mergedSteps.length,
      updatedTime: new Date().toISOString(),
      steps: mergedSteps
    });
  }

  function handleRunStepEvent(event: AgentEvent) {
    if (!event.messageUid || !event.stepUid) return;
    conversationRunsStore.updateRunStep(event.messageUid, event.stepUid, {
      roundIndex: event.payload.roundIndex || 1,
      stepIndex: event.payload.stepIndex || 1,
      status: event.payload.status || "planned",
      displayTitle: event.payload.displayTitle || tr("chat.runtime.processingStep"),
      displaySummary: event.payload.displaySummary || "",
      displayDetails: event.payload.displayDetails || "",
      updatedTime: new Date().toISOString()
    });
  }

  function showApprovalAlert(event: AgentEvent) {
    approval.value = {
      stepUid: event.stepUid || null,
      title: event.payload.title || tr("chat.runtime.highRiskStep"),
      body: `${tr("chat.runtime.stepPrefix", { index: event.payload.stepIndex || "-" })}：${approvalActionSummary(event.payload)}\n\n${tr("chat.runtime.approvalRiskHint")}`,
      riskLevel: event.payload.riskLevel || "HIGH",
      submitting: approval.value.stepUid === (event.stepUid || null) ? approval.value.submitting : false,
      submittingAction: approval.value.stepUid === (event.stepUid || null) ? approval.value.submittingAction : null
    };
  }

  function renderPlanSteps(steps: Record<string, any>[]) {
    if (!steps.length) {
      runtimeLogStore.append(tr("chat.runtime.planCreatedWithoutSteps"));
      return;
    }
    runtimeLogStore.append(tr("chat.runtime.planCreated"));
    steps.forEach((step) => {
      runtimeLogStore.append(
        `${step.stepIndex}. ${step.title}\n   tool=${step.toolName} risk=${step.riskLevel}\n   args=${JSON.stringify(step.toolArgs)}`
      );
    });
  }

  async function handleEvent(event: AgentEvent) {
    const type = event.eventType;
    if (type === "MESSAGE_DELTA") {
      if (!event.messageUid) return;
      const textDelta = String(event.payload.textDelta || "");
      const done = Boolean(event.payload.done);
      const accumulatedText = event.payload.accumulatedText === undefined ? undefined : String(event.payload.accumulatedText || "");
      if (!textDelta && !done) return;
      upsertStreamingAssistantDelta(event.messageUid, textDelta, event.timestamp, accumulatedText, done);
      return;
    }
    if (type === "PLAN_CREATED") {
      if (event.messageUid) {
        clearStreamingAssistantDraft(event.messageUid);
      }
      runtimeLogStore.append(tr("chat.runtime.modelToolCall", { round: event.payload.roundIndex || 1 }));
      renderPlanSteps(event.payload.steps || []);
      handlePlanCreated(event);
      return;
    }
    if (type === "STEP_WAITING_APPROVAL") {
      runtimeLogStore.append(tr("chat.runtime.stepWaitingApproval", { stepUid: event.stepUid }));
      handleRunStepEvent(event);
      showApprovalAlert(event);
      return;
    }
    if (type === "STEP_STARTED") {
      if (approval.value.stepUid && approval.value.stepUid === event.stepUid) {
        clearApproval();
      }
      updateBrowserRuntimeOverlayFromStepEvent(event);
      const silentLog = Boolean(event.payload.silentLog);
      if (!silentLog) {
        runtimeLogStore.append(tr("chat.runtime.stepStarted", { round: event.payload.roundIndex || 1, title: event.payload.title }));
      }
      handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_FINISHED") {
      if (browserRuntimeOverlay.value.stepUid && browserRuntimeOverlay.value.stepUid === (event.stepUid || null)) {
        clearBrowserRuntimeOverlay();
      }
      runtimeLogStore.append(tr("chat.runtime.stepFinished", { title: event.payload.title, output: event.payload.output || "" }));
      handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_FAILED") {
      if (browserRuntimeOverlay.value.stepUid && browserRuntimeOverlay.value.stepUid === (event.stepUid || null)) {
        clearBrowserRuntimeOverlay();
      }
      runtimeLogStore.append(tr("chat.runtime.stepFailed", { title: event.payload.title, errorMessage: event.payload.errorMessage || "" }));
      handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_REJECTED") {
      if (browserRuntimeOverlay.value.stepUid && browserRuntimeOverlay.value.stepUid === (event.stepUid || null)) {
        clearBrowserRuntimeOverlay();
      }
      runtimeLogStore.append(tr("chat.runtime.stepRejected", { title: event.payload.title }));
      handleRunStepEvent(event);
      clearApproval();
      return;
    }
    if (type === "ROUND_TOKEN_USAGE") {
      const round = Number(event.payload.roundIndex || 1);
      const input = Number(event.payload.inputTokens || 0);
      const output = Number(event.payload.outputTokens || 0);
      const total = Number(event.payload.totalTokens || 0);
      const modelName = event.payload.modelName || "-";
      runtimeLogStore.append(tr("chat.runtime.roundTokenUsage", { round, input, output, total, modelName }));
      return;
    }
    if (type === "MESSAGE_COMPLETED") {
      clearApproval();
      clearBrowserRuntimeOverlay();
      if (event.messageUid) {
        clearStreamingAssistantDraft(event.messageUid);
      }
      runtimeLogStore.append(tr("chat.runtime.messageCompleted", { status: event.payload.status, message: event.payload.message }));
      if (event.payload.stopReason) {
        runtimeLogStore.append(tr("chat.runtime.stopReason", {
          reason: event.payload.stopReason,
          roundsUsed: event.payload.roundsUsed,
          maxRounds: event.payload.maxRounds
        }));
      }
      runningConversationUid.value = null;
      if (currentConversationUid.value) {
        await loadMessages(currentConversationUid.value);
        await refreshConversations(currentConversationUid.value);
      }
      return;
    }
    if (type === "LOOP_LIMIT_REACHED") {
      runtimeLogStore.append(tr("chat.runtime.loopLimitReached", {
        maxRounds: event.payload.maxRounds,
        failedStepId: event.payload.failedStepId || "-"
      }));
      return;
    }
    if (type === "MESSAGE_CANCELED") {
      clearApproval();
      clearBrowserRuntimeOverlay();
      if (event.messageUid) {
        clearStreamingAssistantDraft(event.messageUid);
      }
      runtimeLogStore.append(tr("chat.runtime.messageCanceled"));
      const latestUserMessage = [...messages.value].reverse().find((item) => item.role === "user" && item.messageUid);
      if (latestUserMessage?.messageUid) {
        conversationRunsStore.markRunCanceled(latestUserMessage.messageUid);
      }
      runningConversationUid.value = null;
      if (currentConversationUid.value) {
        await loadMessages(currentConversationUid.value);
        await refreshConversations(currentConversationUid.value);
      }
    }
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
    runningConversationUid,
    messages,
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
    selectedModelKey,
    currentUploadPolicy,
    uploadDisabledReason,
    hasAnyConfiguredModel,
    guideToModelSetup,
    init,
    loadModelConfig,
    refreshConversations,
    startDraftConversation,
    selectConversation,
    applyAgentSelection,
    changeRuntimeModel,
    uploadFiles,
    removeDraftAttachment,
    sendMessage,
    cancelRunningMessage,
    approveStep,
    rejectStep,
    openFile,
    renameConversation,
    confirmDeleteConversation
  };
});
