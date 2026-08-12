<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(defineProps<{
  value?: unknown;
  raw?: string;
  level?: number;
}>(), {
  value: undefined,
  raw: undefined,
  level: 0
});

const parseFailed = computed(() => props.raw !== undefined && parsedValue.value === undefined);
const parsedValue = computed(() => {
  if (props.raw === undefined) return props.value;
  try {
    return JSON.parse(props.raw);
  } catch {
    return undefined;
  }
});
const node = computed(() => parsedValue.value);
const isContainer = computed(() => Array.isArray(node.value) || isObject(node.value));
const entries = computed(() => {
  if (Array.isArray(node.value)) return node.value.map((value, index) => [String(index), value] as const);
  if (isObject(node.value)) return Object.entries(node.value);
  return [];
});
const containerLabel = computed(() => Array.isArray(node.value) ? `Array(${entries.value.length})` : `Object(${entries.value.length})`);

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function valueClass(value: unknown) {
  if (value === null) return "json-null";
  if (Array.isArray(value) || isObject(value)) return "json-container";
  return `json-${typeof value}`;
}

function formatPrimitive(value: unknown) {
  if (value === null) return "null";
  if (typeof value === "string") return JSON.stringify(value);
  return String(value);
}
</script>

<template>
  <pre v-if="parseFailed" class="trace-json-invalid">{{ raw || "" }}</pre>
  <details v-else-if="isContainer" class="trace-json-node" :open="level === 0">
    <summary><span class="trace-json-type">{{ containerLabel }}</span></summary>
    <div class="trace-json-children">
      <div v-for="([key, child], index) in entries" :key="`${key}-${index}`" class="trace-json-entry">
        <span class="trace-json-key">{{ key }}:</span>
        <TraceJsonTree :value="child" :level="level + 1" />
      </div>
    </div>
  </details>
  <span v-else class="trace-json-primitive" :class="valueClass(node)">{{ formatPrimitive(node) }}</span>
</template>

<style scoped>
.trace-json-node { font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; }
.trace-json-node summary { cursor: pointer; color: #b9c5d8; user-select: none; }
.trace-json-node summary:hover { color: #f2f3f5; }
.trace-json-type { color: #8fa6c4; }
.trace-json-children { margin: 4px 0 4px 14px; padding-left: 10px; border-left: 1px solid #3a414b; min-width: 0; }
.trace-json-entry { display: block; min-width: 0; max-width: 100%; }
.trace-json-key { color: #8fb8e8; margin-right: 8px; }
.trace-json-primitive { display: inline; max-width: 100%; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.json-string { color: #a6d88a; }
.json-number { color: #f3c878; }
.json-boolean { color: #db9df2; }
.json-null { color: #a5aab3; }
.trace-json-invalid { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
