import { router } from "@/router";
import { message } from "@/discrete";
import { tr } from "@/i18n";

export async function requestJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(input, init);
  } catch (error) {
    const errorMessage = tr("http.networkError");
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
  const withReason = (base: string) => (normalizedReason ? tr("http.withReason", { base, reason: normalizedReason }) : base);
  if (status === 400) {
    return withReason(tr("http.400"));
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
