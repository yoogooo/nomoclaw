import type { ChannelConfig, ChannelTargetSearchResponse } from "@/types/api";
import { requestJson } from "@/utils/http";

export const channelApi = {
  getChannelConfig() {
    return requestJson<ChannelConfig>("/api/system/channels");
  },
  updateChannelConfig(payload: ChannelConfig) {
    return requestJson<ChannelConfig>("/api/system/channels", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  searchChannelTargets(channel: "feishu" | "dingtalk", keyword = "", limit = 20, botId = "") {
    const params = new URLSearchParams({
      channel,
      keyword,
      limit: String(limit),
      botId
    });
    return requestJson<ChannelTargetSearchResponse>(`/api/system/channels/targets/search?${params.toString()}`);
  }
};
