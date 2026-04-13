import { computed, ref } from "vue";
import { defineStore } from "pinia";

type UpdaterStatus = "idle" | "checking" | "available" | "downloading" | "downloaded" | "installing" | "error";

export interface UpdaterStatePayload {
  status: UpdaterStatus;
  latestVersion: string | null;
  downloadProgress: number | null;
  errorMessage: string | null;
}

const DEFAULT_STATE: UpdaterStatePayload = {
  status: "idle",
  latestVersion: null,
  downloadProgress: null,
  errorMessage: null
};

type InvokeFn = <T>(command: string, args?: Record<string, unknown>) => Promise<T>;
type UnlistenFn = () => void;

function isTauriDesktopEnvironment(): boolean {
  if (typeof window === "undefined") return false;
  return "__TAURI_INTERNALS__" in window;
}

function normalizeState(payload: Partial<UpdaterStatePayload> | null | undefined): UpdaterStatePayload {
  const status = payload?.status;
  const validStatus: UpdaterStatus[] = ["idle", "checking", "available", "downloading", "downloaded", "installing", "error"];
  return {
    status: status && validStatus.includes(status) ? status : "idle",
    latestVersion: payload?.latestVersion || null,
    downloadProgress: typeof payload?.downloadProgress === "number" ? payload.downloadProgress : null,
    errorMessage: payload?.errorMessage || null
  };
}

export const useUpdateManagerStore = defineStore("update-manager", () => {
  const state = ref<UpdaterStatePayload>({ ...DEFAULT_STATE });
  const supported = ref(false);
  const ready = ref(false);
  const busy = ref(false);

  let invokeApi: InvokeFn | null = null;
  let unlisten: UnlistenFn | null = null;

  const status = computed(() => state.value.status);
  const latestVersion = computed(() => state.value.latestVersion);
  const downloadProgress = computed(() => state.value.downloadProgress);
  const errorMessage = computed(() => state.value.errorMessage);
  const canInstall = computed(() => status.value === "downloaded");

  async function init() {
    if (ready.value) return;
    if (!isTauriDesktopEnvironment()) return;
    try {
      const [{ invoke }, { listen }] = await Promise.all([
        import("@tauri-apps/api/core"),
        import("@tauri-apps/api/event")
      ]);

      invokeApi = invoke;
      supported.value = true;
      ready.value = true;

      const initial = await invokeApi<UpdaterStatePayload>("updater_get_state");
      state.value = normalizeState(initial);

      unlisten = await listen<UpdaterStatePayload>("updater://state", (event) => {
        state.value = normalizeState(event.payload);
      });
    } catch {
      supported.value = false;
      ready.value = false;
      invokeApi = null;
    }
  }

  async function checkNow() {
    if (!invokeApi || busy.value) return;
    busy.value = true;
    try {
      await invokeApi<void>("updater_check_now");
    } finally {
      busy.value = false;
    }
  }

  async function installDownloaded() {
    if (!invokeApi || busy.value) return;
    busy.value = true;
    try {
      await invokeApi<void>("updater_install_downloaded");
    } finally {
      busy.value = false;
    }
  }

  function dispose() {
    if (unlisten) {
      unlisten();
      unlisten = null;
    }
  }

  return {
    state,
    supported,
    ready,
    busy,
    status,
    latestVersion,
    downloadProgress,
    errorMessage,
    canInstall,
    init,
    checkNow,
    installDownloaded,
    dispose
  };
});
