<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import TraceJsonTree from "./TraceJsonTree.vue";

defineProps<{
  rawText: string;
}>();

const { t } = useI18n();
const mode = ref<"raw" | "json">("raw");
</script>

<template>
  <div class="trace-json-viewer">
    <div class="trace-format-switch" role="tablist" :aria-label="t('chat.trace.format')">
      <button type="button" :class="{ active: mode === 'raw' }" @click="mode = 'raw'">{{ t("chat.trace.rawFormat") }}</button>
      <button type="button" :class="{ active: mode === 'json' }" @click="mode = 'json'">{{ t("chat.trace.jsonFormat") }}</button>
    </div>
    <pre v-if="mode === 'raw'" class="trace-json-raw">{{ rawText || "—" }}</pre>
    <TraceJsonTree v-else :raw="rawText" />
  </div>
</template>

<style scoped>
.trace-json-viewer { min-width: 0; }
.trace-format-switch { display: inline-flex; gap: 2px; margin-bottom: 10px; padding: 2px; border: 1px solid #343a43; border-radius: 6px; background: #121418; }
.trace-format-switch button { border: 0; border-radius: 4px; padding: 3px 8px; background: transparent; color: #9da3ad; font: 11px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; cursor: pointer; }
.trace-format-switch button:hover, .trace-format-switch button.active { background: #2a2f37; color: #f2f3f5; }
.trace-json-raw { margin: 0; max-height: 420px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; }
</style>
