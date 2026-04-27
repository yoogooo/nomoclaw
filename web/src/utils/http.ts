import { router } from "@/router";
import { message } from "@/discrete";
import { tr } from "@/i18n";

const ERROR_TOAST_DEDUP_WINDOW_MS = 2500;
const errorToastLastShownAt = new Map<string, number>();

export interface RequestJsonOptions {
  suppressErrorToast?: boolean;
}

export async function requestJson<T>(
  input: RequestInfo | URL,
  init?: RequestInit,
  options?: RequestJsonOptions
): Promise<T> {
  const suppressErrorToast = Boolean(options?.suppressErrorToast);
  let response: Response;
  try {
    response = await fetch(input, init);
  } catch (error) {
    const errorMessage = tr("http.networkError");
    if (!suppressErrorToast) {
      showErrorToastDedup(errorMessage);
    }
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
    } else if (!suppressErrorToast) {
      showErrorToastDedup(userFriendlyMessage);
    }
    throw new Error(userFriendlyMessage);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

function showErrorToastDedup(text: string) {
  const normalized = (text || "").trim();
  if (!normalized) {
    return;
  }
  const now = Date.now();
  const lastShownAt = errorToastLastShownAt.get(normalized) || 0;
  if (now - lastShownAt < ERROR_TOAST_DEDUP_WINDOW_MS) {
    return;
  }
  errorToastLastShownAt.set(normalized, now);
  if (errorToastLastShownAt.size > 32) {
    // Keep map bounded.
    for (const [key, ts] of errorToastLastShownAt) {
      if (now - ts > ERROR_TOAST_DEDUP_WINDOW_MS * 4) {
        errorToastLastShownAt.delete(key);
      }
    }
  }
  message.error(normalized);
}

function normalizeErrorMessage(text: string): string {
  if (!text) {
    return "";
  }
  try {
    const payload = JSON.parse(text) as {
      message?: string;
      error?: string | { message?: string; reason?: string; detail?: string };
      reason?: string;
      detail?: string;
      status?: string;
    };
    if (typeof payload.error === "object" && payload.error) {
      const nested = payload.error.message || payload.error.reason || payload.error.detail;
      if (nested) {
        return nested;
      }
    }
    if (typeof payload.error === "string" && payload.error.trim()) {
      return payload.error;
    }
    return payload.message || payload.reason || payload.detail || payload.status || text;
  } catch {
    return text;
  }
}

function buildUserFriendlyMessage(status: number, reason: string): string {
  const normalizedReason = reason?.trim() || "";
  const withReason = (base: string) => (normalizedReason ? tr("http.withReason", { base, reason: normalizedReason }) : base);
  if (status === 400) {
    return normalizedReason || tr("http.400");
  }
  if (status === 401) {
    return withReason(tr("http.401"));
  }
  if (status === 403) {
    return withReason(tr("http.403"));
  }
  if (status === 404) {
    return withReason(tr("http.404"));
  }
  if (status === 409) {
    return withReason(tr("http.409"));
  }
  if (status === 413) {
    return tr("http.413", {
      maxFileSize: "20MB",
      maxRequestSize: "40MB"
    });
  }
  if (status === 422) {
    return withReason(tr("http.422"));
  }
  if (status === 429) {
    return withReason(tr("http.429"));
  }
  if (status >= 500) {
    return withReason(tr("http.500"));
  }
  return withReason(tr("http.default", { status }));
}
