import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { tr } from "@/i18n";
import type { ConversationMessageRun, ConversationRunStep } from "@/types/api";

function localizeStepDisplayTitle(step: ConversationRunStep) {
  const toolName = String(step.toolName || "");
  const args = step.toolArgs || {};
  if (toolName === "BrowserTool") {
    const action = String((args as Record<string, unknown>).action || "");
    if (action === "open" || action === "navigate") return tr("chat.runtime.stepTitle.browserOpen");
    if (action === "extract_text") return tr("chat.runtime.stepTitle.browserExtractText");
    if (action === "click") return tr("chat.runtime.stepTitle.browserClick");
    if (action === "type") return tr("chat.runtime.stepTitle.browserType");
    if (action === "wait_for") return tr("chat.runtime.stepTitle.browserWaitFor");
    if (action === "screenshot") return tr("chat.runtime.stepTitle.browserScreenshot");
    if (action === "download") return tr("chat.runtime.stepTitle.browserDownload");
    if (action === "press_key") return tr("chat.runtime.stepTitle.browserPressKey");
    if (action === "snapshot") return tr("chat.runtime.stepTitle.browserSnapshot");
    return tr("chat.runtime.stepTitle.browserDefault");
  }
  if (toolName === "CommandTool") {
    const command = String((args as Record<string, unknown>).command || "").trim();
    if (command) return tr("chat.runtime.stepTitle.commandRunWithCommand", { command });
    return tr("chat.runtime.stepTitle.commandRun");
  }
  if (toolName === "Reasoning") return tr("chat.runtime.stepTitle.reasoning");
  if (toolName === "WebSearchTool") return tr("chat.runtime.stepTitle.webSearch");
  if (toolName === "WebFetchTool") return tr("chat.runtime.stepTitle.webFetch");
  if (toolName === "ReadFileTool") return tr("chat.runtime.stepTitle.fileRead");
  if (toolName === "ListFileTool") return tr("chat.runtime.stepTitle.fileList");
  if (toolName === "CreateFileTool") return tr("chat.runtime.stepTitle.fileCreate");
  if (toolName === "EditFileTool") return tr("chat.runtime.stepTitle.fileEdit");
  if (toolName === "FileSearchTool") return tr("chat.runtime.stepTitle.fileSearch");
  if (toolName === "ImageLoaderTool") return tr("chat.runtime.stepTitle.imageAnalyze");
  if (toolName === "DesktopScreenshotTool") return tr("chat.runtime.stepTitle.desktopScreenshot");
  if (toolName === "CronCreateTool") return tr("chat.runtime.stepTitle.cronCreate");
  if (toolName === "CronDeleteTool") return tr("chat.runtime.stepTitle.cronDelete");
  if (toolName === "CronListTool") return tr("chat.runtime.stepTitle.cronList");
  return step.displayTitle || tr("chat.runtime.processingStep");
}

function localizeRunSteps(run: ConversationMessageRun) {
  run.steps = (run.steps || []).map((step) => ({
    ...step,
    displayTitle: localizeStepDisplayTitle(step)
  }));
}

function normalizeRunSummary(run: ConversationMessageRun) {
  localizeRunSteps(run);
  const steps = [...(run.steps || [])].sort(
    (a, b) => Number(a.roundIndex || 0) - Number(b.roundIndex || 0)
      || Number(a.stepIndex || 0) - Number(b.stepIndex || 0)
  );
  run.steps = steps;
  const completedSteps = steps.filter((step) => step.status === "completed").length;
  let status = run.status || "planned";

  if (status !== "canceled") {
    if (steps.length > 0) {
      const lastStepStatus = steps[steps.length - 1].status || "planned";
      status = lastStepStatus === "rejected" ? "failed" : lastStepStatus;
    } else {
      status = "planned";
    }
  }

  run.status = status;
  run.completedSteps = completedSteps;
  run.totalSteps = steps.length;

  if (status === "completed") {
    run.summary = tr("chat.runSummary.completed", { completedSteps, totalSteps: steps.length });
  } else if (status === "failed") {
    run.summary = tr("chat.runSummary.failed", { completedSteps, totalSteps: steps.length });
  } else if (status === "rejected") {
    run.summary = tr("chat.runSummary.rejected", { completedSteps, totalSteps: steps.length });
  } else if (status === "canceled") {
    run.summary = tr("chat.runSummary.canceled", { completedSteps, totalSteps: steps.length });
  } else if (status === "waiting_approval") {
    run.summary = tr("chat.runSummary.waitingApproval", { completedSteps, totalSteps: steps.length });
  } else if (status === "running") {
    run.summary = tr("chat.runSummary.running", { completedSteps, totalSteps: steps.length });
  } else {
    run.summary = tr("chat.runSummary.planned", { totalSteps: steps.length });
  }
}

export const useConversationRunsStore = defineStore("conversationRuns", () => {
  const runsByMessageUid = ref<Record<string, ConversationMessageRun>>({});
  const expandedKeys = ref<Set<string>>(new Set());

  function clear() {
    runsByMessageUid.value = {};
    expandedKeys.value = new Set();
  }

  function setRuns(runs: ConversationMessageRun[]) {
    runsByMessageUid.value = runs.reduce<Record<string, ConversationMessageRun>>((accumulator, run) => {
      normalizeRunSummary(run);
      accumulator[run.messageUid] = run;
      return accumulator;
    }, {});
  }

  function upsertRun(run: ConversationMessageRun) {
    normalizeRunSummary(run);
    runsByMessageUid.value = {
      ...runsByMessageUid.value,
      [run.messageUid]: run
    };
  }

  function ensureRun(messageUid: string) {
    const existing = runsByMessageUid.value[messageUid];
    if (existing) {
      return existing;
    }
    const created: ConversationMessageRun = {
      messageUid,
      status: "planned",
      summary: tr("chat.runSummary.preparing"),
      completedSteps: 0,
      totalSteps: 0,
      updatedTime: new Date().toISOString(),
      steps: []
    };
    upsertRun(created);
    return created;
  }

  function updateRunStep(messageUid: string, stepUid: string, patch: Partial<ConversationRunStep>) {
    const run = ensureRun(messageUid);
    const steps = [...run.steps];
    const index = steps.findIndex((item) => item.stepUid === stepUid);
    if (index >= 0) {
      const existing = steps[index];
      const nextStep: Partial<ConversationRunStep> = {
        stepUid,
        updatedTime: patch.updatedTime || new Date().toISOString()
      };
      if (patch.roundIndex !== undefined) {
        nextStep.roundIndex = Number(patch.roundIndex);
      }
      if (patch.stepIndex !== undefined) {
        nextStep.stepIndex = Number(patch.stepIndex);
      }
      if (patch.status !== undefined) {
        nextStep.status = patch.status;
      }
      if (patch.displayTitle !== undefined) {
        nextStep.displayTitle = patch.displayTitle;
      }
      if (patch.displaySummary !== undefined) {
        nextStep.displaySummary = patch.displaySummary;
      }
      if (patch.displayDetails !== undefined) {
        nextStep.displayDetails = patch.displayDetails;
      }
      if (patch.toolName !== undefined) {
        nextStep.toolName = patch.toolName;
      }
      if (patch.toolArgs !== undefined) {
        nextStep.toolArgs = patch.toolArgs;
      }
      if (patch.policyReasonCode !== undefined) {
        nextStep.policyReasonCode = patch.policyReasonCode;
      }
      steps[index] = { ...existing, ...nextStep };
    } else {
      steps.push({
        stepUid,
        roundIndex: Number(patch.roundIndex ?? 1),
        stepIndex: Number(patch.stepIndex ?? 1),
        status: patch.status || "planned",
        toolName: patch.toolName || "",
        toolArgs: patch.toolArgs || {},
        displayTitle: patch.displayTitle || tr("chat.runtime.processingStep"),
        displaySummary: patch.displaySummary || "",
        displayDetails: patch.displayDetails || "",
        policyReasonCode: patch.policyReasonCode || "",
        updatedTime: patch.updatedTime || new Date().toISOString()
      });
    }

    upsertRun({
      ...run,
      steps,
      updatedTime: new Date().toISOString()
    });
  }

  function markRunCanceled(messageUid: string) {
    const existing = runsByMessageUid.value[messageUid];
    if (!existing) return;
    const steps = [...(existing.steps || [])];
    const pendingStatuses = new Set(["running", "planned", "waiting_approval"]);
    const pendingIndexes = steps
      .map((step, index) => ({ step, index }))
      .filter(({ step }) => pendingStatuses.has(String(step.status || "").toLowerCase()))
      .sort((left, right) => {
        const leftRound = Number(left.step.roundIndex || 0);
        const rightRound = Number(right.step.roundIndex || 0);
        if (leftRound !== rightRound) {
          return rightRound - leftRound;
        }
        const leftStep = Number(left.step.stepIndex || 0);
        const rightStep = Number(right.step.stepIndex || 0);
        return rightStep - leftStep;
      });
    if (pendingIndexes.length) {
      const target = pendingIndexes[0];
      steps[target.index] = {
        ...target.step,
        status: "canceled",
        updatedTime: new Date().toISOString()
      };
    }
    upsertRun({
      ...existing,
      status: "canceled",
      steps,
      updatedTime: new Date().toISOString()
    });
  }

  function toggleStep(messageUid: string, stepUid: string) {
    const key = `${messageUid}:${stepUid}`;
    const next = new Set(expandedKeys.value);
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    expandedKeys.value = next;
  }

  const expandedKeySet = computed(() => expandedKeys.value);

  return {
    runsByMessageUid,
    expandedKeySet,
    clear,
    setRuns,
    upsertRun,
    ensureRun,
    updateRunStep,
    markRunCanceled,
    toggleStep
  };
});
