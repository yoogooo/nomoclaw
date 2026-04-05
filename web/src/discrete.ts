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
        type: "default",
        color: "#3A3D44",
        textColor: "#E7EAEE"
      }
    };
  }
  return {
    showIcon: false,
    positiveButtonProps: {
      type: "error"
    }
  };
}
