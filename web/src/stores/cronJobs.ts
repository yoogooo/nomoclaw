import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { cronApi, type CreateCronJobPayload } from "@/api/cronApi";
import { fileApi } from "@/api/fileApi";
import { dialog, message, warningDialogPreset } from "@/discrete";
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
    message.success("任务已触发执行");
  }

  async function createJob(payload: CreateCronJobPayload) {
    const created = await cronApi.createCronJob(payload);
    await refresh(created.jobUid);
    message.success("任务已创建");
    return created;
  }

  async function pauseJob(jobUid: string) {
    await cronApi.pauseCronJob(jobUid);
    await refresh(jobUid);
    message.success("任务已暂停");
  }

  async function resumeJob(jobUid: string) {
    await cronApi.resumeCronJob(jobUid);
    await refresh(jobUid);
    message.success("任务已恢复");
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
    message.success("任务已更新");
  }

  async function deleteJob(jobUid: string) {
    await cronApi.deleteCronJob(jobUid);
    const nextJobUid = jobs.value.find((job) => job.jobUid !== jobUid)?.jobUid || null;
    await refresh(nextJobUid);
    message.success("任务已删除");
  }

  function confirmDeleteJob(job: CronJob) {
    dialog.warning({
      title: "删除定时任务",
      content: `确认删除“${job.title || job.taskContent || "未命名任务"}”？`,
      ...warningDialogPreset(),
      positiveText: "删除",
      negativeText: "取消",
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

    const preview = selectedJobs.slice(0, 3).map((job) => `- ${job.title || job.taskContent || "未命名任务"}`).join("\n");
    const suffix = selectedJobs.length > 3 ? `\n- 以及其他 ${selectedJobs.length - 3} 项` : "";

    dialog.warning({
      title: "批量删除任务",
      content: `确认删除选中的 ${selectedJobs.length} 个定时任务？\n\n${preview}${suffix}`,
      ...warningDialogPreset(),
      positiveText: "删除",
      negativeText: "取消",
      onPositiveClick: async () => {
        const result = await cronApi.batchDeleteCronJobs(selectedJobs.map((job) => job.jobUid));
        const nextJobUid = jobs.value.find((job) => !selectedBulkJobUids.value.includes(job.jobUid))?.jobUid || null;
        clearBulkMode();
        await refresh(nextJobUid);
        if (!result.failedItems.length) {
          message.success(`已删除 ${result.deletedJobUids.length} 个任务`);
          return;
        }
        message.warning(`成功删除 ${result.deletedJobUids.length} 个任务，失败 ${result.failedItems.length} 个`);
      }
    });
  }

  async function openReportFile(path: string) {
    await fileApi.openFile(path);
  }

  async function updateSubscriptions(jobUid: string, payload: Array<{ channel: string; target: string; enabled: boolean }>) {
    const updated = await cronApi.updateCronSubscriptions(jobUid, { subscriptions: payload });
    currentSubscriptions.value = updated;
    message.success("推送订阅已保存");
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
