import type { GlobalThemeOverrides } from "naive-ui";
import { themeTokens } from "@/themeTokens";
import type { UiThemeMode } from "@/stores/uiPreferences";

function notificationOverrides(mode: UiThemeMode) {
  const tokens = themeTokens.component.notification[mode];
  return {
    width: "336px",
    padding: "14px 16px",
    borderRadius: "18px",
    titleFontSize: "15px",
    descriptionFontSize: "13px",
    color: tokens.color,
    textColor: tokens.descriptionColor,
    headerTextColor: tokens.titleColor,
    descriptionTextColor: tokens.descriptionColor,
    iconColor: tokens.iconColor,
    closeIconColor: tokens.closeColor,
    closeIconColorHover: tokens.closeColorHover,
    closeIconColorPressed: tokens.closeColorHover,
    boxShadow: tokens.shadow
  };
}

function buildLightThemeOverrides(): GlobalThemeOverrides {
  return {
    common: {
      primaryColor: themeTokens.semantic.light.primary,
      primaryColorHover: themeTokens.semantic.light.primaryHover,
      primaryColorPressed: themeTokens.semantic.light.primaryPressed,
      primaryColorSuppl: themeTokens.semantic.light.primary,
      infoColor: themeTokens.semantic.light.info,
      successColor: themeTokens.semantic.light.success,
      warningColor: themeTokens.semantic.light.warning,
      warningColorHover: "#B15F24",
      warningColorPressed: "#9C4F18",
      errorColor: themeTokens.semantic.light.error,
      bodyColor: themeTokens.semantic.light.bgPage,
      cardColor: themeTokens.semantic.light.bgSurface,
      modalColor: themeTokens.semantic.light.bgSurface,
      popoverColor: themeTokens.semantic.light.bgSurface,
      borderColor: themeTokens.semantic.light.border,
      textColorBase: themeTokens.semantic.light.textPrimary,
      textColor1: themeTokens.semantic.light.textPrimary,
      textColor2: themeTokens.semantic.light.textSecondary,
      textColor3: themeTokens.semantic.light.textTertiary,
      borderRadius: themeTokens.radius.base,
      fontFamily: themeTokens.font.sans,
      fontFamilyMono: themeTokens.font.mono
    },
    Layout: {
      color: themeTokens.semantic.light.bgPage,
      siderColor: themeTokens.semantic.light.bgSurface,
      headerColor: "rgba(255, 255, 255, 0.9)"
    },
    Card: {
      color: themeTokens.semantic.light.bgSurface,
      colorEmbedded: themeTokens.semantic.light.bgSurfaceSoft
    },
    Input: {
      color: themeTokens.component.input.light.color,
      colorFocus: themeTokens.component.input.light.colorFocus,
      colorFocusError: themeTokens.component.input.light.colorFocusError,
      textColor: themeTokens.component.input.light.textColor,
      border: themeTokens.component.input.light.border,
      borderHover: themeTokens.component.input.light.borderHover,
      borderFocus: themeTokens.component.input.light.borderFocus,
      borderError: themeTokens.component.input.light.borderError,
      borderFocusError: themeTokens.component.input.light.borderFocusError
    },
    DataTable: {
      tdColor: themeTokens.semantic.light.bgSurface,
      thColor: themeTokens.semantic.light.bgSurfaceSoft
    },
    Button: {
      borderRadiusTiny: themeTokens.radius.base,
      borderRadiusSmall: themeTokens.radius.base,
      borderRadiusMedium: themeTokens.radius.base,
      borderRadiusLarge: themeTokens.radius.base
    },
    Notification: {
      ...notificationOverrides("light")
    },
    Tag: {
      borderRadius: themeTokens.radius.base
    }
  };
}

function buildDarkThemeOverrides(): GlobalThemeOverrides {
  return {
    common: {
      primaryColor: themeTokens.semantic.dark.primary,
      primaryColorHover: themeTokens.semantic.dark.primaryHover,
      primaryColorPressed: themeTokens.semantic.dark.primaryPressed,
      primaryColorSuppl: themeTokens.semantic.dark.primary,
      infoColor: themeTokens.semantic.dark.info,
      successColor: themeTokens.semantic.dark.success,
      warningColor: themeTokens.semantic.dark.warning,
      errorColor: themeTokens.semantic.dark.error,
      bodyColor: themeTokens.semantic.dark.bgPage,
      cardColor: themeTokens.semantic.dark.bgSurface,
      modalColor: themeTokens.semantic.dark.overlayBase,
      popoverColor: themeTokens.semantic.dark.overlayElevated,
      borderColor: themeTokens.semantic.dark.border,
      textColorBase: themeTokens.semantic.dark.textPrimary,
      textColor1: themeTokens.semantic.dark.textPrimary,
      textColor2: themeTokens.semantic.dark.textSecondary,
      textColor3: themeTokens.semantic.dark.textTertiary,
      borderRadius: themeTokens.radius.base,
      fontFamily: themeTokens.font.sans,
      fontFamilyMono: themeTokens.font.mono
    },
    Layout: {
      color: themeTokens.semantic.dark.bgPage,
      siderColor: themeTokens.semantic.dark.bgSurface,
      headerColor: "rgba(14, 20, 26, 0.92)"
    },
    Card: {
      color: themeTokens.semantic.dark.bgSurface,
      colorEmbedded: themeTokens.semantic.dark.bgSurfaceSoft
    },
    Input: {
      color: themeTokens.component.input.dark.color,
      colorFocus: themeTokens.component.input.dark.colorFocus,
      colorFocusError: themeTokens.component.input.dark.colorFocusError,
      textColor: themeTokens.component.input.dark.textColor,
      border: themeTokens.component.input.dark.border,
      borderHover: themeTokens.component.input.dark.borderHover,
      borderFocus: themeTokens.component.input.dark.borderFocus,
      borderError: themeTokens.component.input.dark.borderError,
      borderFocusError: themeTokens.component.input.dark.borderFocusError
    },
    DataTable: {
      tdColor: themeTokens.semantic.dark.bgSurface,
      thColor: themeTokens.semantic.dark.bgSurfaceSoft
    },
    Button: {
      borderRadiusTiny: themeTokens.radius.base,
      borderRadiusSmall: themeTokens.radius.base,
      borderRadiusMedium: themeTokens.radius.base,
      borderRadiusLarge: themeTokens.radius.base
    },
    Notification: {
      ...notificationOverrides("dark")
    },
    Tooltip: {
      color: themeTokens.semantic.dark.overlayElevated,
      textColor: "#F7F8FA",
      boxShadow: `${themeTokens.semantic.dark.overlayShadowFloating}, inset 0 0 0 1px ${themeTokens.semantic.dark.overlayBorder}`
    },
    Tag: {
      borderRadius: themeTokens.radius.base
    }
  };
}

export function resolveThemeOverrides(mode: UiThemeMode = "light"): GlobalThemeOverrides {
  return mode === "dark" ? buildDarkThemeOverrides() : buildLightThemeOverrides();
}

export const themeOverrides: GlobalThemeOverrides = buildLightThemeOverrides();
