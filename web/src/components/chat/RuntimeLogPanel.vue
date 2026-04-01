<script setup lang="ts">
import { computed } from "vue";
import { ChevronLeft, ChevronRight } from "lucide-vue-next";
import { useRuntimeLogStore } from "@/stores/runtimeLog";

const props = withDefaults(defineProps<{
  collapsed?: boolean;
}>(), {
  collapsed: false
});

const emit = defineEmits<{
  (event: "toggle"): void;
}>();

const runtimeLogStore = useRuntimeLogStore();
const runtimeLines = computed(() => runtimeLogStore.lines);
</script>

<template>
  <aside class="panel panel-soft runtime-panel" :class="{ collapsed: props.collapsed }">
    <div class="runtime-header">
      <div>
        <h2 v-if="!props.collapsed" class="runtime-title">执行日志</h2>
        <p v-if="!props.collapsed" class="runtime-subtitle">统一展示计划生成、审批入口、步骤执行、失败原因和循环终止。</p>
      </div>
      <div class="runtime-tools">
        <button class="runtime-toggle" :title="props.collapsed ? '展开日志' : '收起日志'" @click="emit('toggle')">
          <component :is="props.collapsed ? ChevronLeft : ChevronRight" :size="16" />
        </button>
      </div>
    </div>
    <div v-if="!props.collapsed" class="runtime-body scroll-area">
      <div class="runtime-log-list mono">
        <div v-for="(line, index) in runtimeLines" :key="`${index}-${line}`" class="runtime-line">{{ line }}</div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.runtime-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  border-right: 0;
  background: var(--color-bg-runtime);
}

.runtime-panel.collapsed {
  border-left: var(--size-1) solid var(--color-border-runtime);
}

.runtime-panel.collapsed .runtime-header {
  justify-content: center;
  padding: var(--space-2_5) var(--space-1_5);
}

.runtime-panel.collapsed .runtime-header > div:first-child {
  display: none;
}

.runtime-panel.collapsed .runtime-tools {
  width: 100%;
  justify-content: center;
}

.runtime-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-5);
  border-bottom: var(--size-1) solid var(--color-border-runtime);
}

.runtime-title {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text-runtime);
}

.runtime-subtitle {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-md);
  line-height: 1.5;
  color: var(--color-text-runtime-subtle);
}

.runtime-body {
  flex: 1;
  min-height: 0;
  padding: var(--space-5);
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-runtime-hover) transparent;
}

.runtime-body::-webkit-scrollbar {
  width: var(--size-8);
}

.runtime-body::-webkit-scrollbar-track {
  background: transparent;
}

.runtime-body::-webkit-scrollbar-thumb {
  border-radius: var(--radius-pill);
  background: var(--color-border-runtime-hover);
}

.runtime-tools {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.runtime-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--size-30);
  height: var(--size-30);
  border: var(--size-1) solid var(--color-border-runtime-soft);
  border-radius: var(--radius-pill);
  padding: 0;
  background: var(--color-bg-runtime-btn);
  color: var(--color-text-runtime-button);
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.runtime-toggle:hover {
  background: var(--color-bg-runtime-btn-hover);
  border-color: var(--color-border-runtime-hover);
  color: var(--color-text-runtime);
}

.runtime-log-list {
  display: flex;
  flex-direction: column;
  gap: var(--size-2);
}

.runtime-line {
  padding: var(--space-2) 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: var(--font-size-md);
  line-height: 1.6;
  color: var(--color-text-runtime-line);
}

@media (max-width: var(--size-breakpoint-lg)) {
  .runtime-panel {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    height: 56dvh;
    z-index: 50;
    border-left: 0;
    border-right: 0;
    border-top: var(--size-1) solid var(--color-border-runtime);
  }

  .runtime-header {
    padding: var(--space-3_5) var(--space-4);
  }

  .runtime-body {
    padding: var(--space-3_5) var(--space-4) calc(var(--space-4) + env(safe-area-inset-bottom));
  }
}
</style>
