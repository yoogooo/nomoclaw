import { router } from "@/router";
import { message } from "@/discrete";

export async function requestJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(input, init);
  } catch (error) {
    const errorMessage = "请求未发送成功：网络连接异常或服务不可达，请检查网络/服务后重试。";
    message.error(errorMessage);
    throw new Error(errorMessage, { cause: error });
  }

  if (!response.ok) {
    const text = await response.text();
    const reason = normalizeErrorMessage(text);
    const userFriendlyMessage = buildUserFriendlyMessage(response.status, reason);
    if (response.status === 403 && router.currentRoute.value.name !== "forbidden") {
      void router.replace({
        name: "forbidden",
        query: {
          message: userFriendlyMessage
        }
      });
    } else {
      message.error(userFriendlyMessage);
    }
    throw new Error(userFriendlyMessage);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

function normalizeErrorMessage(text: string): string {
  if (!text) {
    return "";
  }
  try {
    const payload = JSON.parse(text) as { message?: string; error?: string; reason?: string; detail?: string };
    return payload.message || payload.reason || payload.detail || payload.error || text;
  } catch {
    return text;
  }
}

function buildUserFriendlyMessage(status: number, reason: string): string {
  const normalizedReason = reason?.trim() || "";
  const withReason = (base: string) => (normalizedReason ? `${base} 原因：${normalizedReason}` : base);
  if (status === 400) {
    return withReason("请求参数有误，请检查输入后重试。");
  }
  if (status === 401) {
    return withReason("登录状态已失效，请重新登录后重试。");
  }
  if (status === 403) {
    return withReason("当前没有该操作权限。");
  }
  if (status === 404) {
    return withReason("请求的资源不存在或已被删除。");
  }
  if (status === 409) {
    return withReason("资源状态冲突，暂时无法完成操作。");
  }
  if (status === 422) {
    return withReason("提交内容未通过校验，请修改后重试。");
  }
  if (status === 429) {
    return withReason("请求过于频繁，请稍后再试。");
  }
  if (status >= 500) {
    return withReason("服务暂时不可用，请稍后重试。");
  }
  return withReason(`请求失败（HTTP ${status}）。`);
}
