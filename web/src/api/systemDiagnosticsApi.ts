import type { SystemErrorLog, SystemErrorLogSummary } from "@/types/api";
import { requestJson } from "@/utils/http";

export const systemDiagnosticsApi = {
  listErrorLogs(limit = 100, keyword = "") {
    const params = new URLSearchParams();
    params.set("limit", String(limit));
    if (keyword.trim()) {
      params.set("keyword", keyword.trim());
    }
    return requestJson<SystemErrorLog[]>(`/api/system/error-logs?${params.toString()}`);
  },
  getErrorLogSummary() {
    return requestJson<SystemErrorLogSummary>("/api/system/error-logs/summary");
  }
};
