import { computed, ref } from "vue";
import { defineStore } from "pinia";
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
    run.summary = `已完成 ${completedSteps}/${steps.length} 步`;
  } else if (status === "failed") {
    run.summary = `执行失败，已完成 ${completedSteps}/${steps.length} 步`;
  } else if (status === "rejected") {
    run.summary = `执行已拒绝，已完成 ${completedSteps}/${steps.length} 步`;
  } else if (status === "canceled") {
    run.summary = `已取消，已完成 ${completedSteps}/${steps.length} 步`;
  } else if (status === "waiting_approval") {
    run.summary = `等待确认，已完成 ${completedSteps}/${steps.length} 步`;
  } else if (status === "running") {
    run.summary = `正在处理，已完成 ${completedSteps}/${steps.length} 步`;
  } else {
    run.summary = `已规划 ${steps.length} 步，等待开始`;
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
      summary: "正在准备执行步骤",
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
    const nextStep: ConversationRunStep = {
      stepUid,
      roundIndex: Number(patch.roundIndex ?? 1),
      stepIndex: Number(patch.stepIndex ?? 1),
      status: patch.status || "planned",
      displayTitle: patch.displayTitle || "正在处理任务步骤",
      displaySummary: patch.displaySummary || "",
      displayDetails: patch.displayDetails || "",
      updatedTime: patch.updatedTime || new Date().toISOString()
    };

    const steps = [...run.steps];
    const index = steps.findIndex((item) => item.stepUid === stepUid);
    if (index >= 0) {
      steps[index] = { ...steps[index], ...nextStep };
    } else {
      steps.push(nextStep);
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
