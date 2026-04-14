(() => {
  const DEFAULT_LOCALE = "zh-CN";
  const SUPPORTED_LOCALES = ["zh-CN", "en-US"];
  const LOADER_TEXTS = {
    "zh-CN": {
      label: "启动中",
      tip: "提示：页面没更新？右键点一下刷新试试"
    },
    "en-US": {
      label: "Booting Engine",
      tip: "Tip: Right-click to refresh the page"
    }
  };

  function normalizeLocale(value) {
    const raw = String(value || DEFAULT_LOCALE);
    if (SUPPORTED_LOCALES.includes(raw)) return raw;
    const lower = raw.toLowerCase();
    if (lower.startsWith("en")) return "en-US";
    if (lower.startsWith("zh")) return "zh-CN";
    return DEFAULT_LOCALE;
  }

  function resolveLoaderTexts(locale) {
    const normalized = normalizeLocale(locale);
    return LOADER_TEXTS[normalized] || LOADER_TEXTS[DEFAULT_LOCALE];
  }

  window.__NOMOCLAW_LOADER_I18N__ = {
    DEFAULT_LOCALE,
    SUPPORTED_LOCALES,
    LOADER_TEXTS,
    normalizeLocale,
    resolveLoaderTexts
  };
})();
