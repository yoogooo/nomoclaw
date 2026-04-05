import { createDiscreteApi, darkTheme } from "naive-ui";
import type { DialogApi, MessageApi } from "naive-ui";
import type { DialogOptions } from "naive-ui";
import { resolveThemeOverrides } from "@/theme";
import type { UiThemeMode } from "@/stores/uiPreferences";

interface DiscreteApis {
  message: MessageApi;
  dialog: DialogApi;
}

let cachedMode: UiThemeMode | null = null;
let cachedApis: DiscreteApis | null = null;

function resolveThemeMode(): UiThemeMode {
  if (typeof document === "undefined") {
    return "light";
  }
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

function resolveApis(): DiscreteApis {
  const mode = resolveThemeMode();
  if (cachedApis && cachedMode === mode) {
    return cachedApis;
  }
  const apis = createDiscreteApi(["message", "dialog"], {
    messageProviderProps: {
      placement: "top",
      duration: 2200,
      max: 3
    },
    configProviderProps: {
      theme: mode === "dark" ? darkTheme : undefined,
      themeOverrides: resolveThemeOverrides(mode)
    }
  });
  cachedMode = mode;
  cachedApis = apis;
  return apis;
}

function readToken(name: string, fallback: string): string {
  if (typeof document === "undefined") {
    return fallback;
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

function createProxy<T extends object>(key: keyof DiscreteApis): T {
  return new Proxy({} as T, {
    get(_target, prop, receiver) {
      const api = resolveApis()[key] as unknown as Record<PropertyKey, unknown>;
      const value = Reflect.get(api, prop, receiver);
      return typeof value === "function" ? value.bind(api) : value;
    }
  });
}

export const message = createProxy<MessageApi>("message");
export const dialog = createProxy<DialogApi>("dialog");

export function warningDialogPreset(): Pick<DialogOptions, "showIcon" | "positiveButtonProps" | "negativeButtonProps"> {
  if (resolveThemeMode() === "dark") {
    return {
      showIcon: false,
      positiveButtonProps: {
        type: "error"
      },
      negativeButtonProps: {
        secondary: false,
        type: "default",
        color: readToken("--color-button-neutral-bg-dark", "#3A3D44"),
        textColor: readToken("--color-button-neutral-text-dark", "#E7EAEE"),
        themeOverrides: {
          colorHover: readToken("--color-button-neutral-bg-hover-dark", "#454953"),
          colorPressed: readToken("--color-button-neutral-bg-pressed-dark", "#505562")
        }
      }
    };
  }
  return {
    showIcon: false,
    positiveButtonProps: {
      type: "error"
    },
    negativeButtonProps: {
      secondary: false,
      type: "default",
      color: readToken("--color-button-neutral-bg-light", "#EEF1F5"),
      textColor: readToken("--color-button-neutral-text-light", "#4B5563"),
      themeOverrides: {
        colorHover: readToken("--color-button-neutral-bg-hover-light", "#E2E7EE"),
        colorPressed: readToken("--color-button-neutral-bg-pressed-light", "#D6DEE8")
      }
    }
  };
}
