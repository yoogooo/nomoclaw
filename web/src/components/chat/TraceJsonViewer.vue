<script setup lang="ts">
import { nextTick, onMounted, onUpdated, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ChevronDown, Maximize2 } from "lucide-vue-next";
import TraceJsonTree from "./TraceJsonTree.vue";

const props = withDefaults(defineProps<{
  rawText: string;
  title?: string;
  hasExternalCopy?: boolean;
  showFullscreen?: boolean;
}>(), {
  hasExternalCopy: false,
  showFullscreen: true
});

const emit = defineEmits<{
  fullscreen: [content: string];
}>();
const { t } = useI18n();
const mode = ref<"raw" | "json">("raw");
const hasMore = ref(false);
const viewer = ref<HTMLElement | null>(null);

function updateScrollHint() {
  const content = viewer.value?.querySelector<HTMLElement>(".trace-json-raw");
  hasMore.value = Boolean(content && content.scrollHeight > content.clientHeight + 1 && content.scrollTop + content.clientHeight < content.scrollHeight - 4);
}

function onScroll(event: Event) {
  const content = event.currentTarget as HTMLElement;
  hasMore.value = content.scrollTop + content.clientHeight < content.scrollHeight - 4;
}

onMounted(updateScrollHint);
onUpdated(() => void nextTick(updateScrollHint));
</script>

<template>
  <div ref="viewer" class="trace-json-viewer" :class="{ 'has-more': hasMore, 'with-external-copy': props.hasExternalCopy }">
    <div class="trace-viewer-toolbar">
      <div class="trace-format-switch" role="tablist" :aria-label="t('chat.trace.format')">
        <button type="button" :class="{ active: mode === 'raw' }" @click="mode = 'raw'">{{ t("chat.trace.rawFormat") }}</button>
        <button type="button" :class="{ active: mode === 'json' }" @click="mode = 'json'">{{ t("chat.trace.jsonFormat") }}</button>
      </div>
      <button v-if="props.showFullscreen" class="trace-fullscreen" :title="title || t('chat.trace.fullscreen')" @click.stop="emit('fullscreen', rawText)"><Maximize2 :size="14" /></button>
    </div>
    <pre v-if="mode === 'raw'" class="trace-json-raw" @scroll="onScroll">{{ rawText || "—" }}</pre>
    <TraceJsonTree v-else :raw="rawText" />
    <div v-if="hasMore && mode === 'raw'" class="trace-scroll-hint"><ChevronDown :size="16" /><span>{{ t("chat.trace.scrollForMore") }}</span></div>
  </div>
</template>

<style scoped>
.trace-json-viewer { position: relative; min-width: 0; }
.trace-json-viewer.has-more { position: relative; }
.trace-viewer-toolbar { display: flex; align-items: flex-start; justify-content: space-between; min-height: 34px; }
.trace-format-switch { display: inline-flex; gap: 2px; margin-bottom: 10px; padding: 2px; border: 1px solid #343a43; border-radius: 6px; background: #121418; }
.trace-format-switch button { border: 0; border-radius: 4px; padding: 3px 8px; background: transparent; color: #9da3ad; font: 11px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; cursor: pointer; }
.trace-format-switch button:hover, .trace-format-switch button.active { background: #2a2f37; color: #f2f3f5; }
.trace-json-raw { margin: 0; max-height: 420px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; }
.trace-fullscreen { display: grid; place-items: center; margin: 3px 0 0 8px; padding: 4px; border: 0; border-radius: 4px; background: #171b22; color: #aeb6c2; cursor: pointer; }
.trace-fullscreen:hover { background: #2a3039; color: #f2f3f5; }
.trace-scroll-hint { position: absolute; right: 50%; bottom: 8px; display: flex; align-items: center; gap: 5px; padding: 5px 9px; transform: translateX(50%); border: 1px solid rgba(110, 123, 141, 0.45); border-radius: 999px; background: rgba(23, 27, 34, 0.92); color: #c5ccd6; font-size: 11px; pointer-events: none; white-space: nowrap; }
.trace-scroll-hint svg { animation: trace-scroll-hint-bounce 1.4s ease-in-out infinite; color: #7fd1c2; }
@keyframes trace-scroll-hint-bounce { 0%, 100% { transform: translateY(-2px); } 50% { transform: translateY(2px); } }
</style>
