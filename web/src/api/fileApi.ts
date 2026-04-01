import type { SimpleResponse } from "@/types/api";
import { requestJson } from "@/utils/http";

export const fileApi = {
  openFile(path: string) {
    return requestJson<SimpleResponse>("/api/files/open", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path })
    });
  }
};
