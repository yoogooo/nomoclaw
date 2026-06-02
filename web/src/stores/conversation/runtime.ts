import type { AgentEvent, ApprovalMode, ConversationMessageRun, ConversationRunStep } from "@/types/api";
import type { ApprovalState, BrowserRuntimeOverlayState, ConversationStoreContext, ConversationRuntimeModule } from "./types";

const APPROVAL_MODE_STORAGE_KEY = "chat:approval-mode-by-conversation";

function createEmptyApprovalState(): ApprovalState {
  return {
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
  };
}

function createEmptyBrowserRuntimeOverlayState(): BrowserRuntimeOverlayState {
  return {
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
  };
}

export function createConversationRuntimeModule(ctx: ConversationStoreContext): ConversationRuntimeModule {
  const { state, deps } = ctx;

  function loadApprovalModeMap(): Record<string, ApprovalMode> {
    if (typeof window === "undefined") return {};
    const raw = window.localStorage.getItem(APPROVAL_MODE_STORAGE_KEY);
    if (!raw) return {};
    try {
      const parsed = JSON.parse(raw) as Record<string, string>;
      const out: Record<string, ApprovalMode> = {};
      Object.entries(parsed || {}).forEach(([key, value]) => {
        if (!key) return;
        out[key] = value === "full_access" ? "full_access" : "default";
      });
      return out;
    } catch {
      return {};
    }
  }

  function saveApprovalModeMap(map: Record<string, ApprovalMode>) {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(APPROVAL_MODE_STORAGE_KEY, JSON.stringify(map));
  }

  function restoreApprovalModeForConversation(conversationUid: string | null) {
    if (!conversationUid) {
      state.approvalMode.value = "default";
      return;
    }
    const map = loadApprovalModeMap();
    state.approvalMode.value = map[conversationUid] || "default";
  }

  function setApprovalMode(mode: ApprovalMode) {
    state.approvalMode.value = mode;
    if (!state.currentConversationUid.value) {
      return;
    }
    const conversationUid = state.currentConversationUid.value;
    const map = loadApprovalModeMap();
    map[conversationUid] = mode;
    saveApprovalModeMap(map);
    void deps.conversationApi.updateApprovalMode(conversationUid, {
      approvalMode: mode,
      applyToRunning: true
    }).catch((err) => {
      console.warn("[conversation] update approval mode failed", err);
    });
  }

  function clearApproval() {
    state.approval.value = createEmptyApprovalState();
  }

  function clearBrowserRuntimeOverlay() {
    state.browserRuntimeOverlay.value = createEmptyBrowserRuntimeOverlayState();
  }

  function buildBrowserDownloadingDetails(currentArtifact: string, retryCount: number) {
    const artifactLabel = formatBrowserArtifactName(currentArtifact);
    if (artifactLabel) {
      if (retryCount > 0) {
        return `正在重试下载 ${artifactLabel}（第${retryCount + 1}次尝试）`;
      }
      return `正在下载 ${artifactLabel}`;
    }
    if (retryCount > 0) {
      return `正在重试下载浏览器依赖（第${retryCount + 1}次尝试）`;
    }
    return deps.tr("chat.runtime.browserRuntime.downloadingDescSimple");
  }

  function formatBrowserArtifactName(name: string) {
    const normalized = String(name || "").trim().toLowerCase();
    if (!normalized) return "";
    if (normalized === "chromium") return "Chromium";
    if (normalized === "ffmpeg") return "FFmpeg";
    if (normalized === "chromium_headless_shell") return "Chrome Headless Shell";
    return name;
  }

  function updateBrowserRuntimeOverlayFromStepEvent(event: AgentEvent) {
    const toolName = String(event.payload.toolName || "").trim();
    if (toolName !== "BrowserTool") {
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
    const attemptId = String(metrics.attemptId || "");
    const attemptIndexRaw = Number(metrics.attemptIndex ?? 0);
    const attemptIndex = Number.isFinite(attemptIndexRaw)
      ? Math.max(0, Math.round(attemptIndexRaw))
      : 0;
    const retryCountRaw = Number(metrics.retryCount ?? 0);
    const retryCount = Number.isFinite(retryCountRaw)
      ? Math.max(0, Math.round(retryCountRaw))
      : 0;
    const segmentPercentRaw = Number(metrics.segmentPercent ?? -1);
    const segmentPercent = Number.isFinite(segmentPercentRaw)
      ? Math.max(-1, Math.min(100, Math.round(segmentPercentRaw)))
      : -1;
    const overallPercentRaw = Number(metrics.overallPercent ?? -1);
    const overallPercent = Number.isFinite(overallPercentRaw)
      ? Math.max(-1, Math.min(100, Math.round(overallPercentRaw)))
      : -1;
    const currentArtifact = String(metrics.currentArtifact || "");
    const completedArtifacts = Array.isArray(metrics.completedArtifacts)
      ? metrics.completedArtifacts.map((item) => String(item || "")).filter(Boolean)
      : [];
    const indeterminate = Boolean(metrics.indeterminate);
    const title = phase === "checking"
      ? deps.tr("chat.runtime.browserRuntime.checkingTitle")
      : deps.tr("chat.runtime.browserRuntime.downloadingTitle");
    const details = phase === "checking"
      ? deps.tr("chat.runtime.browserRuntime.checkingDesc")
      : buildBrowserDownloadingDetails(currentArtifact, retryCount);
    const hint = deps.tr("chat.runtime.browserRuntime.oneTimeHint");

    if (phase === "checking" || phase === "downloading") {
      const existing = state.browserRuntimeOverlay.value;
      const sameStep = existing.stepUid && existing.stepUid === (event.stepUid || null);
      if (sameStep && existing.attemptIndex > attemptIndex) {
        return;
      }
      state.browserRuntimeOverlay.value = {
        visible: true,
        stepUid: event.stepUid || null,
        attemptId,
        attemptIndex,
        retryCount,
        title,
        details,
        hint,
        downloadedBytes: Math.max(0, downloadedBytes),
        progressPercent,
        indeterminate,
        currentArtifact,
        segmentPercent,
        overallPercent,
        completedArtifacts
      };
      return;
    }
    if (phase === "ready" && state.browserRuntimeOverlay.value.stepUid === (event.stepUid || null)) {
      clearBrowserRuntimeOverlay();
    }
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
    const rendered = deps.resolveApprovalFromStep(current.step);

    state.approval.value = {
      stepUid: current.step.stepUid || null,
      title: deps.tr(rendered.promptKey),
      body: rendered.body,
      labelKey: rendered.labelKey,
      command: rendered.command,
      toolName: rendered.toolName,
      policyReasonCode: current.step.policyReasonCode || "",
      riskLevel: "HIGH",
      submitting: state.approval.value.stepUid === current.step.stepUid ? state.approval.value.submitting : false,
      submittingAction: state.approval.value.stepUid === current.step.stepUid ? state.approval.value.submittingAction : null
    };
  }

  function clearStreamingAssistantDraft(parentMessageUid?: string) {
    if (!parentMessageUid) {
      const indexes = Object.values(state.streamingAssistantByParentUid.value);
      if (!indexes.length) return;
      const indexSet = new Set(indexes);
      state.messages.value = state.messages.value.filter((_, index) => !indexSet.has(index));
      state.streamingAssistantByParentUid.value = {};
      return;
    }
    const index = state.streamingAssistantByParentUid.value[parentMessageUid];
    if (index === undefined) return;
    state.messages.value = state.messages.value.filter((_, i) => i !== index);
    const nextMap: Record<string, number> = {};
    for (const [key, value] of Object.entries(state.streamingAssistantByParentUid.value)) {
      if (key === parentMessageUid) continue;
      nextMap[key] = value > index ? value - 1 : value;
    }
    state.streamingAssistantByParentUid.value = nextMap;
  }

  function upsertStreamingAssistantDelta(parentMessageUid: string, textDelta: string, createdTime?: string, accumulatedText?: string, done?: boolean) {
    if (!parentMessageUid) return;
    const existingIndex = state.streamingAssistantByParentUid.value[parentMessageUid];
    if (existingIndex !== undefined && state.messages.value[existingIndex]) {
      const existing = state.messages.value[existingIndex];
      state.messages.value[existingIndex] = {
        ...existing,
        content: done && accumulatedText !== undefined ? accumulatedText : `${existing.content || ""}${textDelta}`,
        status: done ? "COMPLETED" : "RUNNING"
      };
      return;
    }
    if (!textDelta && accumulatedText === undefined) return;
    state.messages.value = [...state.messages.value, {
      role: "assistant",
      parentMessageUid,
      content: accumulatedText ?? textDelta,
      status: done ? "COMPLETED" : "RUNNING",
      createdTime: createdTime || new Date().toISOString(),
      fileLinks: [],
      attachments: []
    }];
    state.streamingAssistantByParentUid.value = {
      ...state.streamingAssistantByParentUid.value,
      [parentMessageUid]: state.messages.value.length - 1
    };
  }

  function resetRuntimePanels() {
    deps.runtimeLogStore.clear();
    deps.conversationRunsStore.clear();
    clearApproval();
    clearBrowserRuntimeOverlay();
    clearStreamingAssistantDraft();
  }

  function handlePlanCreated(event: AgentEvent) {
    const displaySteps = event.payload.displaySteps || [];
    if (!event.messageUid) return;
    const existingRun = deps.conversationRunsStore.runsByMessageUid[event.messageUid];
    const stepsByUid = new Map<string, ConversationRunStep>();

    (existingRun?.steps || []).forEach((step) => {
      if (!step.stepUid) return;
      stepsByUid.set(step.stepUid, step);
    });

    displaySteps.forEach((step: Record<string, any>) => {
      const stepUid = step.stepUid || "";
      if (!stepUid) return;
      const rendered = deps.resolveApprovalFromPayload(step);
      const normalized: ConversationRunStep = {
        stepUid,
        roundIndex: Number(step.roundIndex || 1),
        stepIndex: Number(step.stepIndex || 1),
        status: step.status || "planned",
        toolName: rendered.toolName,
        toolArgs: step.toolArgs && typeof step.toolArgs === "object" ? step.toolArgs : {},
        displayTitle: step.displayTitle || deps.tr("chat.runtime.processingStep"),
        displaySummary: step.displaySummary || "",
        displayDetails: step.displayDetails || "",
        updatedTime: step.updatedTime || new Date().toISOString()
      };
      const previous = stepsByUid.get(stepUid);
      stepsByUid.set(stepUid, previous ? { ...previous, ...normalized } : normalized);
    });

    deps.conversationRunsStore.upsertRun({
      messageUid: event.messageUid,
      status: existingRun?.status || "planned",
      summary: existingRun?.summary || "",
      completedSteps: existingRun?.completedSteps || 0,
      totalSteps: stepsByUid.size,
      updatedTime: new Date().toISOString(),
      steps: [...stepsByUid.values()]
    });
  }

  function handleRunStepEvent(event: AgentEvent) {
    if (!event.messageUid || !event.stepUid) return;
    const rendered = deps.resolveApprovalFromPayload(event.payload || {});
    deps.conversationRunsStore.updateRunStep(event.messageUid, event.stepUid, {
      roundIndex: event.payload.roundIndex || 1,
      stepIndex: event.payload.stepIndex || 1,
      status: event.payload.status || "planned",
      toolName: rendered.toolName,
      toolArgs: event.payload.toolArgs && typeof event.payload.toolArgs === "object" ? event.payload.toolArgs : undefined,
      displayTitle: event.payload.displayTitle || deps.tr("chat.runtime.processingStep"),
      displaySummary: event.payload.displaySummary || "",
      displayDetails: event.payload.displayDetails || "",
      policyReasonCode: event.payload.policyReasonCode || "",
      updatedTime: new Date().toISOString()
    });
  }

  function handleReasoningEvent(event: AgentEvent) {
    if (!event.messageUid) return;
    const roundIndex = Number(event.payload.roundIndex || 1);
    deps.conversationRunsStore.updateRunStep(event.messageUid, `reasoning-${event.messageUid}-${roundIndex}`, {
      roundIndex,
      stepIndex: 0,
      status: "completed",
      toolName: "Reasoning",
      displayTitle: deps.tr("chat.runtime.stepTitle.reasoning"),
      displaySummary: deps.tr("chat.runtime.reasoningSummary"),
      displayDetails: String(event.payload.content || ""),
      updatedTime: new Date().toISOString()
    });
  }

  function showApprovalAlert(event: AgentEvent) {
    const rendered = deps.resolveApprovalFromPayload(event.payload || {});
    state.approval.value = {
      stepUid: event.stepUid || null,
      title: deps.tr(rendered.promptKey),
      body: rendered.body,
      labelKey: rendered.labelKey,
      command: rendered.command,
      toolName: rendered.toolName,
      policyReasonCode: String(event.payload.policyReasonCode || ""),
      riskLevel: event.payload.riskLevel || "HIGH",
      submitting: state.approval.value.stepUid === (event.stepUid || null) ? state.approval.value.submitting : false,
      submittingAction: state.approval.value.stepUid === (event.stepUid || null) ? state.approval.value.submittingAction : null
    };
  }

  function renderPlanSteps(steps: Record<string, any>[]) {
    if (!steps.length) {
      deps.runtimeLogStore.append(deps.tr("chat.runtime.planCreatedWithoutSteps"));
      return;
    }
    deps.runtimeLogStore.append(deps.tr("chat.runtime.planCreated"));
    steps.forEach((step) => {
      deps.runtimeLogStore.append(
        `${step.stepIndex}. ${step.title}\n   tool=${step.toolName} risk=${step.riskLevel}\n   args=${JSON.stringify(step.toolArgs)}`
      );
    });
  }

  return {
    setApprovalMode,
    clearApproval,
    clearBrowserRuntimeOverlay,
    restoreApprovalModeForConversation,
    restoreApprovalFromRuns,
    updateBrowserRuntimeOverlayFromStepEvent,
    clearStreamingAssistantDraft,
    upsertStreamingAssistantDelta,
    resetRuntimePanels,
    handlePlanCreated,
    handleRunStepEvent,
    handleReasoningEvent,
    showApprovalAlert,
    renderPlanSteps
  };
}
