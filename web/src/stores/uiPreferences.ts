import { computed, ref } from "vue";
import { defineStore } from "pinia";

export type UiThemeMode = "light" | "dark";

const THEME_STORAGE_KEY = "ui:theme-mode";

function normalizeThemeMode(value: string | null | undefined): UiThemeMode {
  return value === "dark" ? "dark" : "light";
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
  const themeMode = ref<UiThemeMode>("light");
  const ready = ref(false);
  const isDarkTheme = computed(() => themeMode.value === "dark");

  function init() {
    if (ready.value) {
      return;
    }
    if (typeof window !== "undefined") {
      const cached = window.localStorage.getItem(THEME_STORAGE_KEY);
      themeMode.value = normalizeThemeMode(cached);
    }
    applyThemeAttribute(themeMode.value);
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

  return {
    themeMode,
    isDarkTheme,
    init,
    setThemeMode,
    toggleThemeMode
  };
});
