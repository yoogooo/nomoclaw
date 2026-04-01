import { createDiscreteApi } from "naive-ui";
import { themeOverrides } from "@/theme";

export const { message, dialog } = createDiscreteApi(["message", "dialog"], {
  messageProviderProps: {
    placement: "top",
    duration: 2200,
    max: 3
  },
  configProviderProps: {
    themeOverrides
  }
});
