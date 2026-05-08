import type {
  AgentCatalogGroup,
  BatchDeleteCronJobsResponse,
  CronJob,
  CronJobExecutionResult,
  CronJobReport,
  CronSubscription,
  CronExecutionDetail,
  SimpleResponse
} from "@/types/api";
import { requestJson } from "@/utils/http";

export interface UpdateCronJobPayload {
  agentUid?: string;
  title?: string;
  expression?: string;
  timezone?: string;
  endAt?: string;
  taskContent?: string;
  status?: string;
}

export interface CreateCronJobPayload {
  agentUid: string;
  title?: string;
  expression: string;
  timezone?: string;
  endAt?: string;
  taskContent: string;
  status?: string;
}

export interface UpdateCronSubscriptionsPayload {
  subscriptions: Array<{
    channel: string;
    target: string;
    botId?: string;
    enabled: boolean;
  }>;
}

export const cronApi = {
  listCronJobs() {
    return requestJson<CronJob[]>("/api/cron-jobs");
  },
  createCronJob(payload: CreateCronJobPayload) {
    return requestJson<CronJob>("/api/cron-jobs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  getCronJob(jobUid: string) {
    return requestJson<CronJob>(`/api/cron-jobs/${jobUid}`);
  },
  getCronJobReport(jobUid: string) {
    return requestJson<CronJobReport>(`/api/cron-jobs/${jobUid}/report`);
  },
  listCronJobResults(jobUid: string) {
    return requestJson<CronJobExecutionResult[]>(`/api/cron-jobs/${jobUid}/results`);
  },
  listGlobalRecentResults(limit = 20) {
    return requestJson<CronJobExecutionResult[]>(`/api/cron-jobs/results/recent?limit=${limit}`);
  },
  getExecutionDetail(executionUid: string, options?: { suppressErrorToast?: boolean }) {
    return requestJson<CronExecutionDetail>(
      `/api/cron-jobs/executions/${executionUid}`,
      undefined,
      options
    );
  },
  markExecutionRead(executionUid: string) {
    return requestJson<SimpleResponse>(`/api/cron-jobs/executions/${executionUid}/read`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({})
    });
  },
  listCronSubscriptions(jobUid: string) {
    return requestJson<CronSubscription[]>(`/api/cron-jobs/${jobUid}/subscriptions`);
  },
  updateCronSubscriptions(jobUid: string, payload: UpdateCronSubscriptionsPayload) {
    return requestJson<CronSubscription[]>(`/api/cron-jobs/${jobUid}/subscriptions`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  runCronJob(jobUid: string) {
    return requestJson<CronJob>(`/api/cron-jobs/${jobUid}/run`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({})
    });
  },
  pauseCronJob(jobUid: string) {
    return requestJson<CronJob>(`/api/cron-jobs/${jobUid}/pause`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({})
    });
  },
  resumeCronJob(jobUid: string) {
    return requestJson<CronJob>(`/api/cron-jobs/${jobUid}/resume`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({})
    });
  },
  updateCronJob(jobUid: string, payload: UpdateCronJobPayload) {
    return requestJson<CronJob>(`/api/cron-jobs/${jobUid}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  deleteCronJob(jobUid: string) {
    return requestJson<SimpleResponse>(`/api/cron-jobs/${jobUid}`, {
      method: "DELETE"
    });
  },
  batchDeleteCronJobs(jobUids: string[]) {
    return requestJson<BatchDeleteCronJobsResponse>("/api/cron-jobs/batch-delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ jobUids })
    });
  },
  listAgentGroups() {
    return requestJson<AgentCatalogGroup[]>("/api/agent-groups");
  }
};
