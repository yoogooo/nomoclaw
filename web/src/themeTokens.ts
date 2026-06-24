export const themeTokens = {
  color: {
    brand: {
      base: "#0F766E",
      hover: "#12958A",
      pressed: "#0B5E58"
    },
    info: "#3B82F6",
    success: "#0F766E",
    warning: "#C06A2B",
    danger: "#B64B36",
    bgPage: "#F5F8FC",
    bgSurface: "#FFFFFF",
    bgSurfaceSoft: "#F8FAFC",
    border: "#94A3B83D",
    textPrimary: "#172033",
    textSecondary: "#475569",
    textTertiary: "#64748B"
  },
  semantic: {
    light: {
      bgCanvas: "#F1F5FB",
      bgPage: "#F5F8FC",
      bgSurface: "#FFFFFF",
      bgSurfaceSoft: "#F9FBFE",
      overlayBase: "#FFFFFF",
      overlayElevated: "#FFFFFF",
      overlaySidepanel: "#FCFDFE",
      overlayBorder: "rgba(148, 163, 184, 0.2)",
      overlayBorderStrong: "rgba(148, 163, 184, 0.28)",
      overlayMask: "rgba(15, 23, 42, 0.26)",
      overlayShadow: "0 24px 72px rgba(15, 23, 42, 0.12)",
      overlayShadowFloating: "0 12px 32px rgba(15, 23, 42, 0.12)",
      border: "#D8E3EE",
      textPrimary: "#142338",
      textSecondary: "#475569",
      textTertiary: "#64748B",
      primary: "#0F766E",
      primaryHover: "#12958A",
      primaryPressed: "#0B5E58",
      info: "#3B82F6",
      success: "#0F766E",
      warning: "#C06A2B",
      error: "#B64B36"
    },
    dark: {
      bgCanvas: "#111214",
      bgPage: "#131416",
      bgSurface: "#1C1D20",
      bgSurfaceSoft: "#242528",
      overlayBase: "#23252A",
      overlayElevated: "#2A2D33",
      overlaySidepanel: "#202227",
      overlayBorder: "rgba(255, 255, 255, 0.12)",
      overlayBorderStrong: "rgba(255, 255, 255, 0.2)",
      overlayMask: "rgba(4, 6, 9, 0.72)",
      overlayShadow: "0 28px 72px rgba(0, 0, 0, 0.52)",
      overlayShadowFloating: "0 16px 36px rgba(0, 0, 0, 0.46)",
      border: "rgba(255, 255, 255, 0.1)",
      textPrimary: "#F2F3F5",
      textSecondary: "#C2C5CA",
      textTertiary: "#979CA5",
      primary: "#36CBB5",
      primaryHover: "#4FDBC7",
      primaryPressed: "#2AAE9A",
      info: "#69B6FF",
      success: "#40D27F",
      warning: "#F0B35A",
      error: "#FF6B6B"
    }
  },
  component: {
    agent: {
      avatarColors: ["#2F6FED", "#138A72", "#F59E0B", "#8B5CF6", "#0EA5E9", "#EF4444", "#14B8A6", "#334155"]
    },
    button: {
      light: {
        defaultColor: "#0F766E",
        defaultColorHover: "#12958A",
        defaultColorPressed: "#0B5E58",
        defaultTextColor: "#FFFFFF",
        defaultBorderColor: "#0F766E",
        defaultBorderColorHover: "#12958A",
        defaultBorderColorPressed: "#0B5E58",
        defaultColorDisabled: "#94A3B85C",
        defaultTextColorDisabled: "#E2E8F0",
        defaultBorderColorDisabled: "#94A3B85C",
        focusRing: "#0F766E26"
      },
      dark: {
        defaultColor: "#36CBB5",
        defaultColorHover: "#4FDBC7",
        defaultColorPressed: "#2AAE9A",
        defaultTextColor: "#F8FAFC",
        defaultBorderColor: "#36CBB5",
        defaultBorderColorHover: "#4FDBC7",
        defaultBorderColorPressed: "#2AAE9A",
        defaultColorDisabled: "#2A2B2E",
        defaultTextColorDisabled: "#80848B",
        defaultBorderColorDisabled: "#35373C",
        focusRing: "rgba(54, 203, 181, 0.35)"
      }
    },
    input: {
      light: {
        color: "#F8FAFC",
        colorFocus: "#FFFFFF",
        colorFocusError: "#FFF7F5",
        textColor: "#172033",
        border: "1px solid rgba(148, 163, 184, 0.24)",
        borderHover: "1px solid rgba(148, 163, 184, 0.35)",
        borderFocus: "1px solid #0F766E",
        borderError: "1px solid #B64B36",
        borderFocusError: "1px solid #B64B36"
      },
      dark: {
        color: "#26272B",
        colorFocus: "#2D2E33",
        colorFocusError: "#322427",
        textColor: "#ECEEF2",
        border: "1px solid rgba(255,255,255,0.12)",
        borderHover: "1px solid rgba(255,255,255,0.2)",
        borderFocus: "1px solid #36CBB5",
        borderError: "1px solid #FF6B6B",
        borderFocusError: "1px solid #FF6B6B"
      }
    },
    notification: {
      light: {
        color: "rgba(255, 255, 255, 0.96)",
        titleColor: "#142338",
        descriptionColor: "#475569",
        iconColor: "#64748B",
        closeColor: "#64748B",
        closeColorHover: "#142338",
        shadow: "inset 0 0 0 1px rgba(216, 227, 238, 0.9)"
      },
      dark: {
        color: "rgba(36, 37, 40, 0.96)",
        titleColor: "#F2F3F5",
        descriptionColor: "#C2C5CA",
        iconColor: "#979CA5",
        closeColor: "#979CA5",
        closeColorHover: "#F2F3F5",
        shadow: "inset 0 0 0 1px rgba(255, 255, 255, 0.09)"
      },
      success: {
        lightColor: "#0F766E",
        lightBg: "rgba(15, 118, 110, 0.1)",
        lightBorder: "rgba(15, 118, 110, 0.16)",
        darkColor: "#6CE3C1",
        darkBg: "rgba(54, 203, 181, 0.14)",
        darkBorder: "rgba(54, 203, 181, 0.18)"
      },
      warning: {
        lightColor: "#B9732F",
        lightBg: "rgba(192, 106, 43, 0.1)",
        lightBorder: "rgba(192, 106, 43, 0.16)",
        darkColor: "#F1BA72",
        darkBg: "rgba(240, 179, 90, 0.14)",
        darkBorder: "rgba(240, 179, 90, 0.18)"
      }
    }
  },
  radius: {
    base: "10px"
  },
  font: {
    sans: "\"IBM Plex Sans\", \"PingFang SC\", sans-serif",
    mono: "\"IBM Plex Mono\", \"SFMono-Regular\", monospace"
  },
  layout: {
    breakpointLg: "1120px"
  }
} as const;
