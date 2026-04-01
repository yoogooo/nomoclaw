import { ref } from "vue";
import { defineStore } from "pinia";
import { appendTimestampPrefix } from "@/utils/format";

export const useRuntimeLogStore = defineStore("runtimeLog", () => {
  const lines = ref<string[]>([]);

  function append(message: string) {
    lines.value.push(appendTimestampPrefix(message));
  }

  function clear() {
    lines.value = [];
  }

  return {
    lines,
    append,
    clear
  };
});
