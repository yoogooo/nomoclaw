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
  const bulkMode = ref(false);
  const selectedBulkJobUids = ref<string[]>([]);
  const loading = ref(false);

  const currentJob = computed(() => jobs.value.find((job) => job.jobUid === selectedJobUid.value) || null);

  function clearBulkMode() {
    bulkMode.value = false;
    selectedBulkJobUids.value = [];
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
      const [nextJobs, nextGroups] = await Promise.all([
        cronApi.listCronJobs(),
        cronApi.listAgentGroups()
      ]);
      jobs.value = nextJobs;
      agentGroups.value = nextGroups;
      selectedBulkJobUids.value = selectedBulkJobUids.value.filter((jobUid) =>
        jobs.value.some((job) => job.jobUid === jobUid)
      );

      if (!jobs.value.length) {
        selectedJobUid.value = null;
        currentSubscriptions.value = [];
        currentResults.value = [];
        clearBulkMode();
        return;
      }

      const nextSelection = preferredJobUid && jobs.value.some((job) => job.jobUid === preferredJobUid)
        ? preferredJobUid
        : selectedJobUid.value && jobs.value.some((job) => job.jobUid === selectedJobUid.value)
          ? selectedJobUid.value
          : jobs.value[0].jobUid;

      if (nextSelection) {
        await selectJob(nextSelection);
      }
    } finally {
      loading.value = false;
    }
  }

  function toggleBulkMode() {
    bulkMode.value = !bulkMode.value;
    if (!bulkMode.value) {
      selectedBulkJobUids.value = [];
    }
  }

  function toggleBulkSelection(jobUid: string, checked: boolean) {
    const next = new Set(selectedBulkJobUids.value);
    if (checked) {
      next.add(jobUid);
    } else {
      next.delete(jobUid);
    }
    selectedBulkJobUids.value = Array.from(next);
  }

  async function runJob(jobUid: string) {
    await cronApi.runCronJob(jobUid);
    await refresh(jobUid);
    message.success(tr("toast.taskTriggered"));
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
    title: string;
    expression: string;
    timezone: string;
    endAt?: string;
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

  async function batchDeleteSelected() {
    const selectedJobs = jobs.value.filter((job) => selectedBulkJobUids.value.includes(job.jobUid));
    if (!selectedJobs.length) {
      return;
    }

    const preview = selectedJobs.slice(0, 3).map((job) => `- ${job.title || job.taskContent || tr("format.fallbackNoName")}`).join("\n");
    const suffix = selectedJobs.length > 3 ? tr("cron.batchDeleteSuffix", { count: selectedJobs.length - 3 }) : "";

    dialog.warning({
      title: tr("dialogs.deleteCronBatchTitle"),
      content: tr("dialogs.deleteCronBatchContent", { count: selectedJobs.length, preview, suffix }),
      ...warningDialogPreset(),
      positiveText: tr("dialogs.confirmDelete"),
      negativeText: tr("common.cancel"),
      onPositiveClick: async () => {
        const result = await cronApi.batchDeleteCronJobs(selectedJobs.map((job) => job.jobUid));
        const nextJobUid = jobs.value.find((job) => !selectedBulkJobUids.value.includes(job.jobUid))?.jobUid || null;
        clearBulkMode();
        await refresh(nextJobUid);
        if (!result.failedItems.length) {
          message.success(tr("cron.batchDeleteSuccess", { count: result.deletedJobUids.length }));
          return;
        }
        message.warning(tr("cron.batchDeletePartial", {
          success: result.deletedJobUids.length,
          failed: result.failedItems.length
        }));
      }
    });
  }

  async function openReportFile(path: string) {
    await fileApi.openFile(path);
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
    bulkMode,
    selectedBulkJobUids,
    currentJob,
    loading,
    refresh,
    createJob,
    selectJob,
    toggleBulkMode,
    clearBulkMode,
    toggleBulkSelection,
    runJob,
    pauseJob,
    resumeJob,
    updateJob,
    deleteJob,
    confirmDeleteJob,
    batchDeleteSelected,
    openReportFile,
    updateSubscriptions
  };
});
