import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { cronApi, type CreateCronJobPayload } from "@/api/cronApi";
import { fileApi } from "@/api/fileApi";
import { dialog, message, warningDialogPreset } from "@/discrete";
import { tr } from "@/i18n";
import type { AgentCatalogGroup, CronJob, CronJobExecutionResult, CronSubscription } from "@/types/api";

export const useCronJobsStore = defineStore("cronJobs", () => {
  const jobs = ref<CronJob[]>([]);
  const agentGroups = ref<AgentCatalogGroup[]>([]);
  const selectedJobUid = ref<string | null>(null);
  const selectedTab = ref<"config" | "result">("config");
  const currentSubscriptions = ref<CronSubscription[]>([]);
  const currentResults = ref<CronJobExecutionResult[]>([]);
  const loading = ref(false);
  const recentGlobalResults = ref<CronJobExecutionResult[]>([]);
  const runningGlobalResults = ref<CronJobExecutionResult[]>([]);

  const currentJob = computed(() => jobs.value.find((job) => job.jobUid === selectedJobUid.value) || null);
  const ACTIVE_EXECUTION_STATUSES = new Set(["RUNNING", "WAITING_APPROVAL"]);

  function isJobExecutionActive(job: CronJob | undefined) {
    if (!job) return false;
    const jobUid = String(job.jobUid || "").trim();
    if (!jobUid) return false;
    return runningGlobalResults.value.some((item) => String(item.jobUid || "").trim() === jobUid);
  }

  function markJobRunningLocally(jobUid: string) {
    const nowIso = new Date().toISOString();
    jobs.value = jobs.value.map((job) => (
      job.jobUid === jobUid
        ? {
            ...job,
            triggerState: "BLOCKED",
            currentExecutionStatus: "RUNNING",
            currentExecutionStartedTime: job.currentExecutionStartedTime || nowIso,
            updatedTime: nowIso
          }
        : job
    ));
  }

  async function selectJob(jobUid: string) {
    selectedJobUid.value = jobUid;
    if (selectedTab.value !== "config" && selectedTab.value !== "result") {
      selectedTab.value = "config";
    }
    const [subscriptions, results] = await Promise.all([
      cronApi.listCronSubscriptions(jobUid),
      cronApi.listCronJobResults(jobUid)
    ]);
    currentSubscriptions.value = subscriptions;
    currentResults.value = results;
  }

  async function refresh(preferredJobUid: string | null = selectedJobUid.value) {
    loading.value = true;
    try {
      const [nextJobs, nextGroups, recentResults, runningResults] = await Promise.all([
        cronApi.listCronJobs(),
        cronApi.listAgentGroups(),
        cronApi.listGlobalRecentResults(20),
        cronApi.listGlobalRunningResults(100)
      ]);
      const statusByExecutionUid = new Map<string, string>();
      await Promise.all(runningResults.map(async (item) => {
        const executionUid = String(item.executionUid || "").trim();
        if (!executionUid) return;
        try {
          const detail = await cronApi.getExecutionDetail(executionUid, { suppressErrorToast: true });
          const normalizedStatus = String(detail?.status || "").trim();
          if (normalizedStatus) {
            statusByExecutionUid.set(executionUid, normalizedStatus);
          }
        } catch {
          // Best-effort sync.
        }
      }));

      jobs.value = nextJobs.map((job) => {
        const executionUid = String(job.currentExecutionUid || "").trim();
        const syncedStatus = executionUid ? statusByExecutionUid.get(executionUid) : undefined;
        return syncedStatus ? { ...job, currentExecutionStatus: syncedStatus } : job;
      });
      agentGroups.value = nextGroups;
      recentGlobalResults.value = recentResults;
      runningGlobalResults.value = runningResults.map((item) => {
        const executionUid = String(item.executionUid || "").trim();
        const syncedStatus = executionUid ? statusByExecutionUid.get(executionUid) : undefined;
        return syncedStatus ? { ...item, status: syncedStatus } : item;
      });

      if (!jobs.value.length) {
        selectedJobUid.value = null;
        currentSubscriptions.value = [];
        currentResults.value = [];
        recentGlobalResults.value = [];
        runningGlobalResults.value = [];
        return;
      }

      if (preferredJobUid === null) {
        selectedJobUid.value = null;
        currentSubscriptions.value = [];
        currentResults.value = [];
        return;
      }

      const nextSelection = preferredJobUid && jobs.value.some((job) => job.jobUid === preferredJobUid)
        ? preferredJobUid
        : jobs.value[0].jobUid;

      if (nextSelection) {
        await selectJob(nextSelection);
      }
    } finally {
      loading.value = false;
    }
  }

  async function refreshExecutionState() {
    const [nextJobs, recentResults, runningResults] = await Promise.all([
      cronApi.listCronJobs(),
      cronApi.listGlobalRecentResults(20),
      cronApi.listGlobalRunningResults(100)
    ]);
    const statusByExecutionUid = new Map<string, string>();
    await Promise.all(runningResults.map(async (item) => {
      const executionUid = String(item.executionUid || "").trim();
      if (!executionUid) return;
      try {
        const detail = await cronApi.getExecutionDetail(executionUid, { suppressErrorToast: true });
        const normalizedStatus = String(detail?.status || "").trim();
        if (normalizedStatus) {
          statusByExecutionUid.set(executionUid, normalizedStatus);
        }
      } catch {
        // Best-effort sync: keep existing job status when detail is temporarily unavailable.
      }
    }));
    jobs.value = nextJobs.map((job) => {
      const executionUid = String(job.currentExecutionUid || "").trim();
      const syncedStatus = executionUid ? statusByExecutionUid.get(executionUid) : undefined;
      return syncedStatus ? { ...job, currentExecutionStatus: syncedStatus } : job;
    });
    recentGlobalResults.value = recentResults;
    runningGlobalResults.value = runningResults.map((item) => {
      const executionUid = String(item.executionUid || "").trim();
      const syncedStatus = executionUid ? statusByExecutionUid.get(executionUid) : undefined;
      return syncedStatus ? { ...item, status: syncedStatus } : item;
    });
    if (selectedJobUid.value && !jobs.value.some((job) => job.jobUid === selectedJobUid.value)) {
      selectedJobUid.value = null;
      currentSubscriptions.value = [];
      currentResults.value = [];
    }
  }

  async function runJob(jobUid: string) {
    const targetJob = jobs.value.find((job) => job.jobUid === jobUid);
    if (isJobExecutionActive(targetJob)) {
      message.warning("该任务正在执行中，请勿重复触发。");
      return;
    }
    const preferredSelection = selectedJobUid.value;
    const previousJobs = jobs.value.slice();
    markJobRunningLocally(jobUid);
    try {
      const latestJob = await cronApi.runCronJob(jobUid);
      jobs.value = jobs.value.map((job) => (job.jobUid === jobUid ? { ...job, ...latestJob } : job));
      await refresh(preferredSelection);
      message.success(tr("toast.taskTriggered"));
    } catch (error) {
      jobs.value = previousJobs;
      throw error;
    }
  }

  async function createJob(payload: CreateCronJobPayload) {
    const created = await cronApi.createCronJob(payload);
    await refresh(created.jobUid);
    message.success(tr("toast.taskCreated"));
    return created;
  }

  async function pauseJob(jobUid: string) {
    await cronApi.pauseCronJob(jobUid);
    await refresh(jobUid);
    message.success(tr("toast.taskPaused"));
  }

  async function resumeJob(jobUid: string) {
    await cronApi.resumeCronJob(jobUid);
    await refresh(jobUid);
    message.success(tr("toast.taskResumed"));
  }

  async function updateJob(jobUid: string, payload: {
    agentUid?: string;
    title: string;
    expression: string;
    timezone: string;
    endAt?: string;
    modelProvider?: string;
    modelName?: string;
    taskContent: string;
    status: string;
  }) {
    await cronApi.updateCronJob(jobUid, payload);
    await refresh(jobUid);
    message.success(tr("toast.taskUpdated"));
  }

  async function deleteJob(jobUid: string) {
    await cronApi.deleteCronJob(jobUid);
    const nextJobUid = jobs.value.find((job) => job.jobUid !== jobUid)?.jobUid || null;
    await refresh(nextJobUid);
    message.success(tr("toast.taskDeleted"));
  }

  function confirmDeleteJob(job: CronJob) {
    dialog.warning({
      title: tr("dialogs.deleteCronTitle"),
      content: tr("dialogs.deleteCronContent", { title: job.title || job.taskContent || tr("format.fallbackNoName") }),
      ...warningDialogPreset(),
      positiveText: tr("dialogs.confirmDelete"),
      negativeText: tr("common.cancel"),
      onPositiveClick: async () => {
        await deleteJob(job.jobUid);
      }
    });
  }

  async function openReportFile(path: string) {
    await fileApi.openFile(path);
  }

  async function markExecutionRead(executionUid: string) {
    if (!executionUid) {
      return;
    }
    await cronApi.markExecutionRead(executionUid);
    recentGlobalResults.value = recentGlobalResults.value.map((item) => (
      item.executionUid === executionUid
        ? { ...item, unread: false }
        : item
    ));
    currentResults.value = currentResults.value.map((item) => (
      item.executionUid === executionUid
        ? { ...item, unread: false }
        : item
    ));
  }


  async function updateSubscriptions(jobUid: string, payload: Array<{ channel: string; target: string; botId?: string; enabled: boolean }>) {
    const updated = await cronApi.updateCronSubscriptions(jobUid, { subscriptions: payload });
    currentSubscriptions.value = updated;
    message.success(tr("toast.subscriptionsSaved"));
  }

  return {
    jobs,
    agentGroups,
    selectedJobUid,
    selectedTab,
    currentSubscriptions,
    currentResults,
    recentGlobalResults,
    runningGlobalResults,
    currentJob,
    loading,
    refresh,
    refreshExecutionState,
    createJob,
    selectJob,
    runJob,
    pauseJob,
    resumeJob,
    updateJob,
    deleteJob,
    confirmDeleteJob,
    openReportFile,
    markExecutionRead,
    updateSubscriptions
  };
});
