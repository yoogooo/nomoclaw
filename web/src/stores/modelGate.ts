import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { modelApi } from "@/api/modelApi";

const MODEL_GATE_DISMISSED_KEY = "ui:model-gate-dismissed";

function resolveDismissedState() {
  if (typeof window === "undefined") {
    return false;
  }
  return window.localStorage.getItem(MODEL_GATE_DISMISSED_KEY) === "1";
}

function setDismissedState(value: boolean) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(MODEL_GATE_DISMISSED_KEY, value ? "1" : "0");
}

export const useModelGateStore = defineStore("model-gate", () => {
  const ready = ref(false);
  const checking = ref(false);
  const isModelReady = ref(false);
  const checkError = ref<string>("");
  const dismissed = ref(resolveDismissedState());

  const shouldShowPrompt = computed(() =>
    ready.value && !checking.value && !isModelReady.value && !dismissed.value
  );

  async function refreshModelReadiness() {
    checking.value = true;
    checkError.value = "";
    try {
      const config = await modelApi.getAvailableModelConfig();
      isModelReady.value = config.providers.some((provider) =>
        provider.models.some((model) => Boolean(model.id?.trim()))
      );
      if (isModelReady.value) {
        dismissed.value = false;
        setDismissedState(false);
      }
    } catch (error) {
      isModelReady.value = false;
      checkError.value = error instanceof Error ? error.message : "model readiness check failed";
    } finally {
      ready.value = true;
      checking.value = false;
    }
  }

  function dismissPrompt() {
    dismissed.value = true;
    setDismissedState(true);
  }

  function resetPrompt() {
    dismissed.value = false;
    setDismissedState(false);
  }

  return {
    ready,
    checking,
    isModelReady,
    checkError,
    dismissed,
    shouldShowPrompt,
    refreshModelReadiness,
    dismissPrompt,
    resetPrompt
  };
});
