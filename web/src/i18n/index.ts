import { createI18n } from "vue-i18n";
import enUS from "@/locales/en-US";
import zhCN from "@/locales/zh-CN";

export type AppLocale = "zh-CN" | "en-US";
export const SUPPORTED_APP_LOCALES = ["zh-CN", "en-US"] as const;

const LOCALE_STORAGE_KEY = "ui:locale";
const DEFAULT_LOCALE: AppLocale = "zh-CN";
const FALLBACK_LOCALE: AppLocale = "zh-CN";

const messages = {
  "zh-CN": zhCN,
  "en-US": enUS
} as const;

export function normalizeAppLocale(raw: string | null | undefined): AppLocale {
  if (!raw) return DEFAULT_LOCALE;
  const value = raw.toLowerCase();
  if (value.startsWith("zh")) return "zh-CN";
  if (value.startsWith("en")) return "en-US";
  return DEFAULT_LOCALE;
}

function resolveInitialLocale(): AppLocale {
  if (typeof window === "undefined") {
    return DEFAULT_LOCALE;
  }
  const cached = window.localStorage.getItem(LOCALE_STORAGE_KEY);
  if (cached) {
    return normalizeAppLocale(cached);
  }
  return normalizeAppLocale(window.navigator.language);
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: resolveInitialLocale(),
  fallbackLocale: FALLBACK_LOCALE,
  messages
});

export function setI18nLocale(locale: AppLocale) {
  i18n.global.locale.value = locale;
  if (typeof window !== "undefined") {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  }
  if (typeof document !== "undefined") {
    document.documentElement.setAttribute("lang", locale);
  }
}

export function getI18nLocale(): AppLocale {
  return i18n.global.locale.value as AppLocale;
}

export function getSortLocale(): string {
  return getI18nLocale() === "zh-CN" ? "zh-CN" : "en-US";
}

export function getCronLocale(): "zh_CN" | "en" {
  return getI18nLocale() === "zh-CN" ? "zh_CN" : "en";
}

export function tr(key: string, named?: Record<string, unknown>): string {
  return i18n.global.t(key, named || {}) as string;
}
