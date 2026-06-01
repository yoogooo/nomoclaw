import type { SystemErrorLogPage, SystemErrorLogSummary } from "@/types/api";
import { requestJson } from "@/utils/http";

export const systemDiagnosticsApi = {
  listErrorLogs(page = 1, pageSize = 20, keyword = "") {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("pageSize", String(pageSize));
    if (keyword.trim()) {
      params.set("keyword", keyword.trim());
    }
    return requestJson<SystemErrorLogPage>(`/api/system/error-logs?${params.toString()}`);
  },
  getErrorLogSummary() {
    return requestJson<SystemErrorLogSummary>("/api/system/error-logs/summary");
  }
};
