import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { normalizeAppLocale, setI18nLocale } from "@/i18n";
import type { AppLocale } from "@/i18n";

export type UiThemeMode = "light" | "dark";

const THEME_STORAGE_KEY = "ui:theme-mode";
const LOCALE_STORAGE_KEY = "ui:locale";
const CHAT_RUNTIME_LOG_VISIBLE_KEY = "ui:chat-runtime-log-visible";

function isTauriDesktopEnvironment(): boolean {
  if (typeof window === "undefined") return false;
  return "__TAURI_INTERNALS__" in window;
}

function normalizeThemeMode(value: string | null | undefined): UiThemeMode {
  return value === "light" ? "light" : "dark";
}

function applyThemeAttribute(mode: UiThemeMode) {
  if (typeof document === "undefined") {
    return;
  }
  if (mode === "dark") {
    document.documentElement.setAttribute("data-theme", "dark");
    return;
  }
  document.documentElement.removeAttribute("data-theme");
}

export const useUiPreferencesStore = defineStore("ui-preferences", () => {
  const themeMode = ref<UiThemeMode>("dark");
  const locale = ref<AppLocale>("zh-CN");
  const chatRuntimeLogVisible = ref(false);
  const ready = ref(false);
  const isDarkTheme = computed(() => themeMode.value === "dark");
  let bootstrapPersistChain: Promise<void> = Promise.resolve();

  async function invokePersistBootstrapPrefs(nextThemeMode: UiThemeMode, nextLocale: AppLocale) {
    if (!isTauriDesktopEnvironment()) return;
    try {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("save_bootstrap_prefs", {
        payload: {
          themeMode: nextThemeMode,
          locale: nextLocale
        }
      });
    } catch {
      // Ignore desktop bridge errors to avoid blocking UI preference updates.
    }
  }

  function syncBootstrapPrefs() {
    const nextThemeMode = themeMode.value;
    const nextLocale = locale.value;
    bootstrapPersistChain = bootstrapPersistChain.finally(() =>
      invokePersistBootstrapPrefs(nextThemeMode, nextLocale)
    );
  }

  function init() {
    if (ready.value) {
      return;
    }
    if (typeof window !== "undefined") {
      const cached = window.localStorage.getItem(THEME_STORAGE_KEY);
      themeMode.value = normalizeThemeMode(cached);
      const cachedLocale = window.localStorage.getItem(LOCALE_STORAGE_KEY);
      locale.value = cachedLocale ? normalizeAppLocale(cachedLocale) : normalizeAppLocale(window.navigator.language);
      chatRuntimeLogVisible.value = window.localStorage.getItem(CHAT_RUNTIME_LOG_VISIBLE_KEY) === "1";
    }
    applyThemeAttribute(themeMode.value);
    setI18nLocale(locale.value);
    syncBootstrapPrefs();
    ready.value = true;
  }

  function setThemeMode(nextMode: UiThemeMode) {
    themeMode.value = nextMode;
    if (typeof window !== "undefined") {
      window.localStorage.setItem(THEME_STORAGE_KEY, nextMode);
    }
    applyThemeAttribute(nextMode);
    syncBootstrapPrefs();
  }

  function toggleThemeMode() {
    setThemeMode(themeMode.value === "dark" ? "light" : "dark");
  }

  function setLocale(nextLocale: AppLocale) {
    locale.value = nextLocale;
    if (typeof window !== "undefined") {
      window.localStorage.setItem(LOCALE_STORAGE_KEY, nextLocale);
    }
    setI18nLocale(nextLocale);
    syncBootstrapPrefs();
  }

  function setChatRuntimeLogVisible(visible: boolean) {
    chatRuntimeLogVisible.value = visible;
    if (typeof window !== "undefined") {
      window.localStorage.setItem(CHAT_RUNTIME_LOG_VISIBLE_KEY, visible ? "1" : "0");
    }
  }

  return {
    themeMode,
    locale,
    chatRuntimeLogVisible,
    isDarkTheme,
    init,
    setThemeMode,
    toggleThemeMode,
    setLocale,
    setChatRuntimeLogVisible
  };
});
