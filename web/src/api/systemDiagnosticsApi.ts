import type { SystemErrorLog, SystemErrorLogSummary } from "@/types/api";
import { requestJson } from "@/utils/http";

export const systemDiagnosticsApi = {
  listErrorLogs(limit = 100) {
    return requestJson<SystemErrorLog[]>(`/api/system/error-logs?limit=${encodeURIComponent(String(limit))}`);
  },
  getErrorLogSummary() {
    return requestJson<SystemErrorLogSummary>("/api/system/error-logs/summary");
  }
};
