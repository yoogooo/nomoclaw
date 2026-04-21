import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { tr } from "@/i18n";
import type { ConversationMessageRun, ConversationRunStep } from "@/types/api";

function normalizeRunSummary(run: ConversationMessageRun) {
  const steps = run.steps || [];
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
    const nextStep: Partial<ConversationRunStep> = {
      stepUid,
      roundIndex: Number(patch.roundIndex ?? 1),
      stepIndex: Number(patch.stepIndex ?? 1),
      status: patch.status || "planned",
      displayTitle: patch.displayTitle || tr("chat.runtime.processingStep"),
      displaySummary: patch.displaySummary || "",
      displayDetails: patch.displayDetails || "",
      updatedTime: patch.updatedTime || new Date().toISOString()
    };
    if (patch.command !== undefined) {
      nextStep.command = patch.command;
    }
    if (patch.policyReasonCode !== undefined) {
      nextStep.policyReasonCode = patch.policyReasonCode;
    }

    const steps = [...run.steps];
    const index = steps.findIndex((item) => item.stepUid === stepUid);
    if (index >= 0) {
      steps[index] = { ...steps[index], ...nextStep };
    } else {
      steps.push({
        stepUid,
        roundIndex: Number(nextStep.roundIndex ?? 1),
        stepIndex: Number(nextStep.stepIndex ?? 1),
        status: nextStep.status || "planned",
        displayTitle: nextStep.displayTitle || tr("chat.runtime.processingStep"),
        displaySummary: nextStep.displaySummary || "",
        displayDetails: nextStep.displayDetails || "",
        command: nextStep.command || "",
        policyReasonCode: nextStep.policyReasonCode || "",
        updatedTime: nextStep.updatedTime || new Date().toISOString()
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
    upsertRun({
      ...existing,
      status: "canceled"
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
