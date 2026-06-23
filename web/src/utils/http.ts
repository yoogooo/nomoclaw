import { router } from "@/router";
import { message } from "@/discrete";
import { tr } from "@/i18n";
import { getI18nLocale } from "@/i18n";

const ERROR_TOAST_DEDUP_WINDOW_MS = 2500;
const errorToastLastShownAt = new Map<string, number>();

export interface RequestJsonOptions {
  suppressErrorToast?: boolean;
}

export class HttpRequestError extends Error {
  readonly status: number | null;
  readonly reason: string;
  readonly networkError: boolean;

  constructor(message: string, options: { status?: number; reason?: string; networkError?: boolean; cause?: unknown } = {}) {
    super(message, { cause: options.cause });
    this.name = "HttpRequestError";
    this.status = options.status ?? null;
    this.reason = options.reason ?? "";
    this.networkError = Boolean(options.networkError);
  }
}

export async function requestJson<T>(
  input: RequestInfo | URL,
  init?: RequestInit,
  options?: RequestJsonOptions
): Promise<T> {
  const suppressErrorToast = Boolean(options?.suppressErrorToast);
  let response: Response;
  try {
    response = await fetch(input, withLocaleHeader(init));
  } catch (error) {
    const errorMessage = tr("http.serviceNotStarted");
    if (!suppressErrorToast) {
      showErrorToastDedup(errorMessage);
    }
    throw new HttpRequestError(errorMessage, {
      networkError: true,
      cause: error
    });
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
    throw new HttpRequestError(userFriendlyMessage, {
      status: response.status,
      reason
    });
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

function withLocaleHeader(init?: RequestInit): RequestInit {
  const headers = new Headers(init?.headers);
  const locale = getI18nLocale();
  if (!headers.has("Accept-Language")) {
    headers.set("Accept-Language", locale);
  }
  if (!headers.has("X-App-Locale")) {
    headers.set("X-App-Locale", locale);
  }
  return {
    ...init,
    headers
  };
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
  const normalizedReason = translateBackendReason(reason?.trim() || "");
  const withReason = (base: string) => (normalizedReason ? tr("http.withReason", { base, reason: normalizedReason }) : base);
  if (status >= 500 && isBackendUnavailableReason(reason)) {
    return tr("http.serviceNotStarted");
  }
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

function isBackendUnavailableReason(reason: string): boolean {
  const normalized = String(reason || "").trim().toLowerCase();
  if (!normalized) {
    return false;
  }
  return normalized.includes("econnrefused")
    || normalized.includes("connect refused")
    || normalized.includes("proxy error");
}

function translateBackendReason(reason: string): string {
  const normalized = reason.trim();
  if (!normalized) {
    return "";
  }
  if (normalized === "pageSize must be one of 10, 20, 50" || normalized === "pageSize must be 20") {
    return tr("http.invalidPageSize");
  }
  if (normalized.startsWith("invalid status:")) {
    return tr("http.invalidStatus");
  }
  if (normalized === "startDate cannot be later than endDate") {
    return tr("http.invalidDateRange");
  }
  return normalized;
}
