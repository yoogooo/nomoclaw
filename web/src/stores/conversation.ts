import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { conversationApi } from "@/api/conversationApi";
import { fileApi } from "@/api/fileApi";
import { subscribeConversationEvents } from "@/api/eventStreamApi";
import { modelApi } from "@/api/modelApi";
import { dialog, message } from "@/discrete";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationRunsStore } from "@/stores/conversationRuns";
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

const EMPTY_UPLOAD_POLICY: UploadPolicy = {
  enabled: false,
  allowedMimeGroups: [],
  maxFilesPerMessage: 0,
  maxImagesPerMessage: 0,
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

  let eventSource: EventSource | null = null;

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
      return "请先选择模型";
    }
    if (!currentUploadPolicy.value.enabled) {
      return "当前模型不支持上传文件";
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

  function buildApprovalBodyFromStep(step: ConversationRunStep) {
    const details = (step.displayDetails || step.displaySummary || "").trim();
    const main = details || `步骤 ${step.stepIndex || "-"} 需要人工确认后才能继续执行。`;
    return `${main}\n\n风险说明：此操作可能改动本地环境、页面状态或产生不可逆结果。若与当前意图不符，请直接拒绝。`;
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
      title: current.step.displayTitle || "系统准备执行高风险步骤",
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

  function resetRuntimePanels() {
    runtimeLogStore.clear();
    conversationRunsStore.clear();
    clearApproval();
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

  function syncRuntimeModelSelection() {
    const latestUserMessage = [...messages.value]
      .reverse()
      .find((item) => item.role === "user" && item.provider && item.modelName);
    const latestProvider = latestUserMessage
      ? configuredProviders.value.find((provider) => provider.id === latestUserMessage.provider)
      : null;
    if (latestUserMessage?.provider && latestUserMessage?.modelName && latestProvider?.models.some((model) => model.id === latestUserMessage.modelName)) {
      selectedModelProvider.value = latestUserMessage.provider;
      selectedModelName.value = latestUserMessage.modelName;
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
      throw new Error("当前模型不支持上传文件");
    }
    const combinedGroups = new Set<string>([
      ...existing.map((item) => item.mimeGroup),
      ...files.map((file) => resolveMimeGroup(file))
    ]);
    if (!combinedGroups.size) {
      return;
    }
    if (policy.singleMimeGroupOnly && combinedGroups.size > 1) {
      throw new Error("同一条消息只能上传同一类型的文件");
    }
    const unsupportedGroup = [...combinedGroups].find((group) =>
      policy.allowedMimeGroups.length && !policy.allowedMimeGroups.includes(group)
    );
    if (unsupportedGroup) {
      throw new Error("当前模型不支持该类型文件");
    }

    const existingImageCount = existing.filter((item) => item.mimeGroup === "image").length;
    const incomingImageCount = files.filter((file) => resolveMimeGroup(file) === "image").length;
    const imageCount = existingImageCount + incomingImageCount;
    const totalCount = existing.length + files.length;
    const nonImageCount = totalCount - imageCount;

    if (imageCount > 0 && nonImageCount > 0 && !policy.allowMixedImageAndFile) {
      throw new Error("图片和其他文件不能混合上传");
    }
    if (imageCount > 0 && imageCount > policy.maxImagesPerMessage) {
      throw new Error(`当前模型最多上传 ${policy.maxImagesPerMessage} 张图片`);
    }
    if (nonImageCount > 0 && nonImageCount > policy.maxFilesPerMessage) {
      throw new Error(`当前模型最多上传 ${policy.maxFilesPerMessage} 个非图片文件`);
    }
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
    currentConversationUid.value = null;
    runningConversationUid.value = null;
    messages.value = [];
    draftMessage.value = "";
    draftAttachments.value = [];
    resetRuntimePanels();
    disconnectEventSource();
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

    const createGroupUid = agentCatalogStore.selectedEntryType === "group" ? agentCatalogStore.selectedAgentGroupUid : "";
    const createAgentUid = agentCatalogStore.selectedEntryType === "group" ? "" : agentCatalogStore.selectedAgentUid;
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
        clearDraftAttachments("已清空当前草稿附件，新模型不支持这些文件");
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
    try {
      validateFilesAgainstPolicy(currentUploadPolicy.value, draftAttachments.value, files);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "文件不符合当前模型上传规则");
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
      message.error("请先选择模型");
      return;
    }

    const lockedProvider = selectedModelProvider.value;
    const lockedModelName = selectedModelName.value;
    const conversationUid = await ensureConversationForInteraction();
    const effectiveProvider = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedProvider : selectedModelProvider.value;
    const effectiveModelName = hasConfiguredModel(lockedProvider, lockedModelName) ? lockedModelName : selectedModelName.value;
    if (!effectiveProvider || !effectiveModelName) {
      message.error("请先选择模型");
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
      runtimeLogStore.append(`消息已提交 messageUid=${accepted.messageUid}`);
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
      message.success("已批准，任务继续执行");
      runtimeLogStore.append(`审批已提交: approve ${stepUid}`);
    } catch (error) {
      approval.value = {
        ...approval.value,
        submitting: false,
        submittingAction: null
      };
      message.error("批准失败，请重试");
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
      message.warning("已拒绝当前步骤");
      runtimeLogStore.append(`审批已提交: reject ${stepUid}`);
    } catch (error) {
      approval.value = {
        ...approval.value,
        submitting: false,
        submittingAction: null
      };
      message.error("拒绝失败，请重试");
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
        displayTitle: step.displayTitle || "正在处理任务步骤",
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
      displayTitle: event.payload.displayTitle || "正在处理任务步骤",
      displaySummary: event.payload.displaySummary || "",
      displayDetails: event.payload.displayDetails || "",
      updatedTime: new Date().toISOString()
    });
  }

  function showApprovalAlert(event: AgentEvent) {
    approval.value = {
      stepUid: event.stepUid || null,
      title: event.payload.title || "系统准备执行高风险步骤",
      body: `步骤 ${event.payload.stepIndex || "-"}：${approvalActionSummary(event.payload)}\n\n风险说明：此操作可能改动本地环境、页面状态或产生不可逆结果。若与当前意图不符，请直接拒绝。`,
      riskLevel: event.payload.riskLevel || "HIGH",
      submitting: approval.value.stepUid === (event.stepUid || null) ? approval.value.submitting : false,
      submittingAction: approval.value.stepUid === (event.stepUid || null) ? approval.value.submittingAction : null
    };
  }

  function renderPlanSteps(steps: Record<string, any>[]) {
    if (!steps.length) {
      runtimeLogStore.append("计划已生成，但没有可展示的步骤。");
      return;
    }
    runtimeLogStore.append("计划已生成:");
    steps.forEach((step) => {
      runtimeLogStore.append(
        `${step.stepIndex}. ${step.title}\n   tool=${step.toolName} risk=${step.riskLevel}\n   args=${JSON.stringify(step.toolArgs)}`
      );
    });
  }

  async function handleEvent(event: AgentEvent) {
    const type = event.eventType;
    if (type === "PLAN_CREATED") {
      runtimeLogStore.append(`模型请求工具调用 (Round ${event.payload.roundIndex || 1})`);
      renderPlanSteps(event.payload.steps || []);
      handlePlanCreated(event);
      return;
    }
    if (type === "STEP_WAITING_APPROVAL") {
      runtimeLogStore.append(`步骤等待审批: ${event.stepUid}`);
      handleRunStepEvent(event);
      showApprovalAlert(event);
      return;
    }
    if (type === "STEP_STARTED") {
      if (approval.value.stepUid && approval.value.stepUid === event.stepUid) {
        clearApproval();
      }
      runtimeLogStore.append(`开始执行: [Round ${event.payload.roundIndex || 1}] ${event.payload.title}`);
      handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_FINISHED") {
      runtimeLogStore.append(`步骤完成: ${event.payload.title}\n输出: ${event.payload.output || ""}`);
      handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_FAILED") {
      runtimeLogStore.append(`步骤失败: ${event.payload.title}\n错误: ${event.payload.errorMessage || ""}`);
      handleRunStepEvent(event);
      return;
    }
    if (type === "STEP_REJECTED") {
      runtimeLogStore.append(`步骤已被拒绝: ${event.payload.title}`);
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
      runtimeLogStore.append(`Round ${round} Token 使用: input=${input}, output=${output}, total=${total}, model=${modelName}`);
      return;
    }
    if (type === "MESSAGE_COMPLETED") {
      clearApproval();
      runtimeLogStore.append(`消息处理结束: ${event.payload.status} / ${event.payload.message}`);
      if (event.payload.stopReason) {
        runtimeLogStore.append(`停止原因: ${event.payload.stopReason} (${event.payload.roundsUsed}/${event.payload.maxRounds})`);
      }
      runningConversationUid.value = null;
      if (currentConversationUid.value) {
        await loadMessages(currentConversationUid.value);
        await refreshConversations(currentConversationUid.value);
      }
      return;
    }
    if (type === "LOOP_LIMIT_REACHED") {
      runtimeLogStore.append(`达到最大循环次数(${event.payload.maxRounds})，任务终止。失败步骤: ${event.payload.failedStepId || "-"}`);
      return;
    }
    if (type === "MESSAGE_CANCELED") {
      clearApproval();
      runtimeLogStore.append("消息处理已取消");
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
      title: "删除对话",
      content: `确认删除“${title || "未命名对话"}”？删除后不可恢复。`,
      positiveText: "删除",
      negativeText: "取消",
      onPositiveClick: async () => {
        await deleteConversation(conversationUid);
        message.success("对话已删除");
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
    modelConfig,
    availableModelOptions,
    currentModelOption,
    selectedModelProvider,
    selectedModelName,
    selectedModelKey,
    currentUploadPolicy,
    uploadDisabledReason,
    init,
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
