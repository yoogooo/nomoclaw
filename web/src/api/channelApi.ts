import type { ChannelConfig } from "@/types/api";
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
  }
};
