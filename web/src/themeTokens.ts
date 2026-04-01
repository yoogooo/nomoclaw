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
      bgCanvas: "#000000",
      bgPage: "#000000",
      bgSurface: "#0E141A",
      bgSurfaceSoft: "#151D26",
      border: "rgba(255, 255, 255, 0.18)",
      textPrimary: "#F5F8FC",
      textSecondary: "#C4CFDB",
      textTertiary: "#99A9BB",
      primary: "#36CBB5",
      primaryHover: "#4FDBC7",
      primaryPressed: "#2AAE9A",
      info: "#60A5FA",
      success: "#36CBB5",
      warning: "#F0A35B",
      error: "#FB7185"
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
        defaultColorDisabled: "#1C242E",
        defaultTextColorDisabled: "#6D7D90",
        defaultBorderColorDisabled: "#2C3744",
        focusRing: "#36CBB53A"
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
        color: "#111418",
        colorFocus: "#161B20",
        colorFocusError: "#2A1D2B",
        textColor: "#EDF2F7",
        border: "1px solid rgba(255,255,255,0.16)",
        borderHover: "1px solid rgba(255,255,255,0.28)",
        borderFocus: "1px solid #36CBB5",
        borderError: "1px solid #FB7185",
        borderFocusError: "1px solid #FB7185"
      }
    }
  },
  radius: {
    base: "18px"
  },
  font: {
    sans: "\"IBM Plex Sans\", \"PingFang SC\", sans-serif",
    mono: "\"IBM Plex Mono\", \"SFMono-Regular\", monospace"
  },
  layout: {
    breakpointLg: "1120px"
  }
} as const;
