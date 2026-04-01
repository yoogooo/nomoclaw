import type { GlobalThemeOverrides } from "naive-ui";
import { themeTokens } from "@/themeTokens";

export const themeOverrides: GlobalThemeOverrides = {
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
  Tag: {
    borderRadius: themeTokens.radius.base
  }
};
