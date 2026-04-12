import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { setI18nLocale } from "@/i18n";
import type { AppLocale } from "@/i18n";

export type UiThemeMode = "light" | "dark";

const THEME_STORAGE_KEY = "ui:theme-mode";
const LOCALE_STORAGE_KEY = "ui:locale";

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

function normalizeLocale(value: string | null | undefined): AppLocale {
  if (!value) return "zh-CN";
  const normalized = value.toLowerCase();
  if (normalized.startsWith("en")) return "en-US";
  return "zh-CN";
}

export const useUiPreferencesStore = defineStore("ui-preferences", () => {
  const themeMode = ref<UiThemeMode>("dark");
  const locale = ref<AppLocale>("zh-CN");
  const ready = ref(false);
  const isDarkTheme = computed(() => themeMode.value === "dark");

  function init() {
    if (ready.value) {
      return;
    }
    if (typeof window !== "undefined") {
      const cached = window.localStorage.getItem(THEME_STORAGE_KEY);
      themeMode.value = normalizeThemeMode(cached);
      const cachedLocale = window.localStorage.getItem(LOCALE_STORAGE_KEY);
      locale.value = cachedLocale ? normalizeLocale(cachedLocale) : normalizeLocale(window.navigator.language);
    }
    applyThemeAttribute(themeMode.value);
    setI18nLocale(locale.value);
    ready.value = true;
  }

  function setThemeMode(nextMode: UiThemeMode) {
    themeMode.value = nextMode;
    if (typeof window !== "undefined") {
      window.localStorage.setItem(THEME_STORAGE_KEY, nextMode);
    }
    applyThemeAttribute(nextMode);
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
  }

  return {
    themeMode,
    locale,
    isDarkTheme,
    init,
    setThemeMode,
    toggleThemeMode,
    setLocale
  };
});
