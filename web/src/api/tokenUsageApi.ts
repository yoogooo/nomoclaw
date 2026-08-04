import type { TokenUsageOverview, TokenUsageRecordPage } from "@/types/api";
import { requestJson } from "@/utils/http";

export interface TokenUsageFilter { from?: string; to?: string; provider?: string; model?: string; scene?: string; }
function query(filter: TokenUsageFilter, page?: number, pageSize?: number) {
  const params = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => { if (value) params.set(key, value); });
  if (page) params.set("page", String(page));
  if (pageSize) params.set("pageSize", String(pageSize));
  return params.toString();
}
export const tokenUsageApi = {
  overview(filter: TokenUsageFilter) { return requestJson<TokenUsageOverview>(`/api/system/token-usage/overview?${query(filter)}`); },
  page(filter: TokenUsageFilter, page = 1, pageSize = 20) { return requestJson<TokenUsageRecordPage>(`/api/system/token-usage?${query(filter, page, pageSize)}`); }
};
